package com.openrsc.server.plugins.authentic.misc;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Skills;
import com.openrsc.server.content.SkillCapes;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.RuneScript;
import com.openrsc.server.plugins.triggers.OpInvTrigger;
import com.openrsc.server.plugins.triggers.UseInvTrigger;
import com.openrsc.server.util.rsc.MessageType;

import java.util.Optional;

import static com.openrsc.server.plugins.Functions.*;

public class Bones implements OpInvTrigger, UseInvTrigger {

	@Override
	public boolean blockOpInv(Player player, Integer invIndex, Item item, String command) {
		return command.equalsIgnoreCase("bury") && item.getCatalogId() != ItemId.RASHILIYA_CORPSE.id();
	}

	@Override
	public void onOpInv(Player player, Integer invIndex, Item item, String command) {
		if (item.getCatalogId() == ItemId.RASHILIYA_CORPSE.id()) return;
		if (command.equalsIgnoreCase("bury")) {

			if (item.getNoted()) {
				player.message("You can't bury noted bones");
				return;
			}

			int buryAmount = 1;
			if (config().BATCH_PROGRESSION) {
				int invAmount = player.getCarriedItems().getInventory().countId(item.getCatalogId(), Optional.of(false));
				buryAmount = (item.getAmount() > invAmount) ? invAmount : item.getAmount();
			}

			startbatch(buryAmount);
			buryBones(player, item);
		}
	}


	private void buryBones(Player player, Item item) {
		Item toRemove = player.getCarriedItems().getInventory().get(
			player.getCarriedItems().getInventory().getLastIndexById(item.getCatalogId(), Optional.of(false)));
		if(toRemove == null) return;

		player.playerServerMessage(MessageType.QUEST, "you dig a hole in the ground");
		delay();
		player.playerServerMessage(MessageType.QUEST, "You bury the bones");
		if (player.getCarriedItems().remove(toRemove) != -1) {
			giveBonesExperience(player, item);
		}

		if (SkillCapes.shouldActivate(player, ItemId.PRAYER_CAPE)) {
			prayerCape(player, item);
		}

		// Repeat
		updatebatch();
		if (!ifinterrupted() && !isbatchcomplete()) {
			buryBones(player, item);
		}
	}


	private void giveBonesExperience(Player player, Item item) {
		giveBonesExperience(player, item, false);
	}
	private void giveBonesExperience(Player player, Item item, boolean bonecrusher) {

		// TODO: Config for custom sounds.
		//owner.playSound("takeobject");

		int[] prayerSkillIds;
		if (player.getConfig().DIVIDED_GOOD_EVIL) {
			// per Rab, historically gave same xp to praygood and prayevil
			// This was also confirmed by Gugge when both skills leveled same time to 5
			prayerSkillIds = new int[]{Skill.PRAYGOOD.id(), Skill.PRAYEVIL.id()};
		} else {
			prayerSkillIds = new int[]{Skill.PRAYER.id()};
		}
		int factor = player.getConfig().OLD_PRAY_XP ? 3 : 2; // factor to divide by modern is 2 / 2 or 1

		int skillXP = 0;
		switch (ItemId.getById(item.getCatalogId())) {
			case BONES:
				skillXP = 2 * 15; // divided by factor below for 3.75
				break;
			case BAT_BONES:
				skillXP = 2 * 18; // divided by factor below for 4.5
				break;
			case BIG_BONES:
				skillXP = 2 * 50; // divided by factor below for 12.5
				break;
			case DRAGON_BONES:
				skillXP = 2 * 240; // divided by factor below for 60
				break;
			default:
				player.message("Nothing interesting happens");
				break;
		}
		if (skillXP > 0) {
			for (int praySkillId : prayerSkillIds) {
				int xpToGive = skillXP / factor;
				if (bonecrusher) xpToGive /= 2;
				player.incExp(praySkillId, xpToGive, true);
			}
		}
	}


	private void prayerCape(final Player player, final Item bone) {
		final int currentPrayerLevel = player.getSkills().getLevel(Skill.PRAYER.id());
		final int maxPrayerLevel = player.getSkills().getMaxStat(Skill.PRAYER.id());
		if (currentPrayerLevel >= maxPrayerLevel) return;

		int pointsToRestore = 0;
		switch (ItemId.getById(bone.getCatalogId())) {
			case BONES:
			case BAT_BONES:
				pointsToRestore = 1;
				break;
			case BIG_BONES:
				pointsToRestore = 2;
				break;
			case DRAGON_BONES:
				pointsToRestore = 4;
				break;
		}

		if (pointsToRestore > 0) {
			mes("@yel@Your prayer cape activates, restoring " + pointsToRestore + " prayer points!");
			int newPrayer = currentPrayerLevel + pointsToRestore;
			if (newPrayer > maxPrayerLevel) {
				newPrayer = maxPrayerLevel;
			}
			player.getSkills().setLevel(Skill.PRAYER.id(), newPrayer, true);
		}
	}

	@Override
	public void onUseInv(Player player, Integer invIndex, Item item1, Item item2) {
		Item bones;
		if (item1.getCatalogId() == ItemId.BONECRUSHER.id()) {
			bones = item2;
		} else {
			bones = item1;
		}

		mes("You place the bones into the bonecrusher");
		RuneScript.remove(bones.getCatalogId(), 1);
		delay(3);
		mes("The gods are angered by your sacrilege");
		giveBonesExperience(player, bones, true);
	}

	@Override
	public boolean blockUseInv(Player player, Integer invIndex, Item item1, Item item2) {
		if (item1.getCatalogId() == ItemId.BONECRUSHER.id()) {
			return item2.getCatalogId() == ItemId.BONES.id()
				|| item2.getCatalogId() == ItemId.BAT_BONES.id()
				|| item2.getCatalogId() == ItemId.BIG_BONES.id()
				|| item2.getCatalogId() == ItemId.DRAGON_BONES.id();
		} else if (item2.getCatalogId() == ItemId.BONECRUSHER.id()) {
			return item1.getCatalogId() == ItemId.BONES.id()
				|| item1.getCatalogId() == ItemId.BAT_BONES.id()
				|| item1.getCatalogId() == ItemId.BIG_BONES.id()
				|| item1.getCatalogId() == ItemId.DRAGON_BONES.id();
		}
		return false;
	}
}
