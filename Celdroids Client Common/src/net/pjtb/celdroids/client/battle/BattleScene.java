package net.pjtb.celdroids.client.battle;

import java.util.List;

import net.pjtb.celdroids.Constants;
import net.pjtb.celdroids.client.Button;
import net.pjtb.celdroids.client.CeldroidBattleMove;
import net.pjtb.celdroids.client.CeldroidMonster;
import net.pjtb.celdroids.client.Scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFont.TextBounds;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.NumberUtils;

public class BattleScene implements Scene {
	private final BattleModel model;

	private Scene subScene;

	private final FanSelect partySwitcher, attackList;
	private final Button runButton;

	private final AttackAnimation atkAnimation;
	private final SummonAnimation smnAnimation;
	private final DismissAnimation dmAnimation;

	private float fontTint;
	private String text;
	private float remainingTextTime;

	public BattleScene(BattleModel model) {
		this.model = model;

		runButton = new Button(model.parent, "Flee", new Runnable() {
			@Override
			public void run() {
				flee();
			}
		}, 1172, 576, 108, 144, "ui/battleScene/run", "ui/battleScene/selectedRun", 255, 255, 255, 255, 255, 0, 0, 63);

		partySwitcher = new FanSelect(model.parent, 10 + 120 / 2, Constants.HEIGHT / 2, -Math.PI / 3, Math.PI / 3, 60, 200, "Party");
		attackList = new FanSelect(model.parent, 500 + 120 / 2, Constants.HEIGHT / 2, -Math.PI / 3, Math.PI / 3, 60, 200, "Attack");

		atkAnimation = new AttackAnimation(model);
		smnAnimation = new SummonAnimation(model);
		dmAnimation = new DismissAnimation(model);

		fontTint = NumberUtils.intToFloatColor(0xFF << 24 | 0xFF << 16 | 0x00 << 8 | 0x00);
	}

	private void flee() {
		if (model.canAct) {
			subScene = model.subScenes.get(BattleModel.BattleSubSceneType.CONFIRM_FLEE_POPUP);
			subScene.swappedIn(true);
		}
	}

	private void calledMonster() {
		final List<CeldroidBattleMove> moves = model.party.get(0).monsterType.moves;
		String[] moveNames = new String[moves.size()];
		for (int i = 0; i < moveNames.length; i++)
			moveNames[i] = moves.get(i).name;

		attackList.setSelections(new FanSelect.SelectTask() {
			@Override
			public void selected(int index) {
				atkAnimation.reset(moves.get(index));
				model.currentAnimation = atkAnimation;
				text = model.party.get(0).getName() + " used " + atkAnimation.move.name + "!";
				remainingTextTime = 2;
				model.canAct = false;
			}
		}, moveNames);

		smnAnimation.reset();
		model.currentAnimation = smnAnimation;
		text = "Go, " + model.party.get(0).getName() + "!";
		remainingTextTime = 2;
		model.canAct = false;
	}

	@Override
	public void swappedIn(boolean transition) {
		Gdx.gl10.glClearColor(1f, 1f, 1f, 1);

		if (transition) {
			model.selfTurn = model.canAct = false;
			model.updateParty();
			smnAnimation.initial = true;

			final CeldroidMonster[] restOfParty = model.party.subList(1, model.party.size()).toArray(new CeldroidMonster[model.party.size() - 1]);
			final String[] selectablePartyNames = new String[restOfParty.length];
			for (int i = 0; i < restOfParty.length; i++)
				selectablePartyNames[i] = restOfParty[i].getName();
			final FanSelect.SelectTask[] partySwitchTask = new FanSelect.SelectTask[1];
			partySwitchTask[0] = new FanSelect.SelectTask() {
				@Override
				public void selected(int index) {
					model.swapPartyLead(index + 1);
					restOfParty[index] = model.party.get(index + 1);
					for (int i = 0; i < restOfParty.length; i++)
						selectablePartyNames[i] = restOfParty[i].getName();
					partySwitcher.setSelections(partySwitchTask[0], selectablePartyNames);

					dmAnimation.reset(model.party.get(index + 1).monsterType.sprite);
					model.currentAnimation = dmAnimation;
					text = "Come back, " + model.party.get(index + 1).getName() + "!";
					remainingTextTime = 2;
					model.canAct = false;
				}
			};
			partySwitcher.setSelections(partySwitchTask[0], selectablePartyNames);

			model.showOpponentCeldroid = model.showSelfCeldroid = false;
			smnAnimation.reset();
			model.currentAnimation = smnAnimation;
			text = model.op.name + " summoned " + model.op.party.get(0).getName();
			remainingTextTime = 2;
			model.canAct = false;
		}
	}

	@Override
	public void pause() {
		if (subScene != null)
			subScene.pause();
	}

	@Override
	public void resume() {
		if (subScene != null)
			subScene.resume();
	}

	private void checkForOpponentMove() {
		atkAnimation.reset(model.parent.assets.get(model.op.nextMove(), CeldroidBattleMove.class));
		model.currentAnimation = atkAnimation;
		text = "Enemy " + model.op.party.get(0).getName() + " used " + atkAnimation.move.name + "!";
		remainingTextTime = 2;
	}

	@Override
	public void update(float tDelta) {
		runButton.hidden = (subScene != null || !model.canAct);
		runButton.update(tDelta);
		partySwitcher.hidden = (subScene != null || !model.canAct);
		partySwitcher.update(tDelta);
		attackList.hidden = (subScene != null || !model.canAct);
		attackList.update(tDelta);
		if (remainingTextTime > 0) {
			remainingTextTime -= tDelta;
			if (remainingTextTime <= 0) {
				remainingTextTime = 0;
				text = null;
			}
		}
		if (model.currentAnimation != null)
			model.currentAnimation.update(tDelta);
		if (model.currentAnimation == null)
			if (!model.showOpponentCeldroid && !model.selfTurn || !model.showSelfCeldroid && model.selfTurn)
				calledMonster();
			else if (!model.selfTurn)
				checkForOpponentMove();

		if (subScene == null) {
			if (model.parent.controller.wasBackPressed && !Gdx.input.isKeyPressed(Keys.ESCAPE) && !Gdx.input.isKeyPressed(Keys.BACK)) {
				flee();
			} else if (model.parent.controller.wasMenuPressed && !Gdx.input.isKeyPressed(Keys.ENTER) && !Gdx.input.isKeyPressed(Keys.MENU)) {
				
			}
		} else {
			subScene.update(tDelta);
		}
	}

	@Override
	public void draw(SpriteBatch batch) {
		Sprite s = model.parent.sprites.get("character/human1/left/0");
		s.setBounds(10, (Constants.HEIGHT - 120) / 2, 120, 120);
		if (!s.isFlipX())
			s.flip(true, false);
		s.draw(batch);
		if (model.showSelfCeldroid) {
			s = model.parent.sprites.get(model.party.get(0).monsterType.sprite);
			s.setBounds(500, (Constants.HEIGHT - 120) / 2, 120, 120);
			if (!s.isFlipX())
				s.flip(true, false);
			s.draw(batch);
		}

		s = model.parent.sprites.get("character/human2/left/0");
		s.setBounds(Constants.WIDTH - 10 - 120, (Constants.HEIGHT - 120) / 2, 120, 120);
		if (s.isFlipX())
			s.flip(true, false);
		s.draw(batch);
		if (model.showOpponentCeldroid) {
			s = model.parent.sprites.get(model.op.party.get(0).monsterType.sprite);
			s.setBounds(Constants.WIDTH - 200 - 120, (Constants.HEIGHT - 120) / 2, 120, 120);
			if (s.isFlipX())
				s.flip(true, false);
			s.draw(batch);
		}

		if (model.currentAnimation != null)
			model.currentAnimation.draw(batch);

		if (text != null) {
			BitmapFont fnt = model.parent.assets.get("fonts/buttons.fnt", BitmapFont.class);
			TextBounds bnds = fnt.getBounds(text);
			fnt.setColor(fontTint);
			fnt.draw(batch, text, (float) ((Constants.WIDTH - bnds.width) / 2), (float) (50 + bnds.height));
		}

		if (model.canAct) {
			runButton.draw(batch);
			partySwitcher.draw(batch);
			attackList.draw(batch);
		}

		if (subScene != null)
			subScene.draw(batch);
	}

	@Override
	public void swappedOut(boolean transition) {
		
	}

	@Override
	public Scene getSubscene() {
		return subScene;
	}

	@Override
	public void setSubscene(Scene scene) {
		this.subScene = scene;
	}
}
