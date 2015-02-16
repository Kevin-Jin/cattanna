package in.kevinj.colonists.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.AssetLoader;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.async.ThreadUtils;

public class PriorityQueueAssetManager extends AssetManager {
	@SuppressWarnings("rawtypes")
	public static class LoadEntry {
		public final String fileName;
		public final Class type;
		public final AssetLoaderParameters parameter;

		public <T> LoadEntry(String fileName, Class<T> type, AssetLoaderParameters<T> parameter) {
			this.fileName = fileName;
			this.type = type;
			this.parameter = parameter;
		}
	}

	private static class PriorityList {
		private final Map<String, LoadEntry> entries;
		public int notLoadedCount;

		public PriorityList() {
			entries = new HashMap<String, LoadEntry>();
		}

		public Iterator<LoadEntry> iterator() {
			return entries.values().iterator();
		}

		public void add(LoadEntry entry) {
			entries.put(entry.fileName, entry);
		}

		public LoadEntry remove(String fileName) {
			return entries.remove(fileName);
		}
	}

	private final Map<Integer, PriorityList> queuedToLoad;
	private final Map<String, PriorityList> fileNameToPriority;

	public PriorityQueueAssetManager() {
		queuedToLoad = new TreeMap<Integer, PriorityList>();
		fileNameToPriority = new HashMap<String, PriorityList>();
	}

	/**
	 * Either add the file to the load queue or change the priority of the
	 * existing entry in the queue.
	 * @param fileName
	 * @param type
	 * @param parameter
	 * @param priority
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public <T> void queueOrMove(String fileName, Class<T> type, AssetLoaderParameters<T> parameter, int priority) {
		AssetLoader loader = getLoader(type);
		Array<AssetDescriptor> deps = loader.getDependencies(fileName, loader.resolve(fileName), parameter);

		PriorityList priorityList = queuedToLoad.get(Integer.valueOf(priority));
		if (priorityList == null) {
			priorityList = new PriorityList();
			queuedToLoad.put(Integer.valueOf(priority), priorityList);
		}

		LoadEntry entry;
		PriorityList oldList;
		if (isLoaded(fileName)) //container asset, we're only interested in dependencies
			entry = null;
		else if ((oldList = fileNameToPriority.get(fileName)) != null) //change priority
			entry = oldList.remove(fileName);
		else //add new item to queue
			entry = new LoadEntry(fileName, type, parameter);

		//change priority of existing dependencies, if any
		if (deps != null) {
			for (AssetDescriptor desc : deps) {
				if (isLoaded(desc.fileName))
					continue;

				oldList = fileNameToPriority.get(desc.fileName);
				if (oldList != null)
					priorityList.add(oldList.remove(desc.fileName));
				else if (entry == null)
					priorityList.add(new LoadEntry(desc.fileName, desc.type, desc.params));
				fileNameToPriority.put(desc.fileName, priorityList);
			}
		}

		if (entry != null) {
			priorityList.add(entry);
			fileNameToPriority.put(fileName, priorityList);
		}
	}

	/**
	 * Unlike the base AssetManager method:<br>
	 * 1.) you must call {@link #startLoading()} in order to begin loading the
	 *     assets after queuing them, and<br>
	 * 2.) the same file cannot be loaded more than once (have its reference
	 *     count incremented)
	 */
	@Override
	public <T> void load(String fileName, Class<T> type, AssetLoaderParameters<T> parameter) {
		queueOrMove(fileName, type, parameter, Integer.MAX_VALUE);
	}

	@SuppressWarnings("unchecked")
	public void startLoading() {
		for (PriorityList priorityList : queuedToLoad.values()) {
			for (Iterator<LoadEntry> iter = priorityList.iterator(); iter.hasNext(); ) {
				LoadEntry entry = iter.next();
				super.load(entry.fileName, entry.type, entry.parameter);
				iter.remove();
				priorityList.notLoadedCount++;
			}
		}
	}

	@Override
	protected <T> void addAsset(final String fileName, Class<T> type, T asset) {
		super.addAsset(fileName, type, asset);
		PriorityList priorityList = fileNameToPriority.remove(fileName);
		//asserting that dependencies will always be loaded before addAsset() is
		//called for their parents.
		if (priorityList != null)
			priorityList.notLoadedCount--;
	}

	public void finishLoading(int priority) {
		PriorityList priorityList = queuedToLoad.get(Integer.valueOf(priority));
		while (!update() && priorityList.notLoadedCount != 0)
			ThreadUtils.yield();
	}

	public boolean update(int priority) {
		if (getQueuedAssets() == 0)
			return true;

		PriorityList priorityList = queuedToLoad.get(Integer.valueOf(priority));
		return update() || priorityList.notLoadedCount == 0;
	}
}
