package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.constants.IronmanMode;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.container.*;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.net.rsc.OpcodeIn;
import com.openrsc.server.net.rsc.PacketHandler;

public final class BankHandler implements PacketHandler {

	public void handlePacket(Packet packet, Player player) {

		//Restrict access to Ultimate Ironmen
		if (player.isIronMan(IronmanMode.Ultimate.id())) {
			player.message("As an Ultimate Iron Man, you cannot use the bank.");
			player.resetBank();
			return;
		}

		//Make sure they can't access the bank while busy
		if (player.isBusy() || player.isRanging() || player.getTrade().isTradeActive()
			|| player.getDuel().isDuelActive() || player.inCombat()) {
			player.resetBank();
			return;
		}

		//Make sure they are at a banker
		if (!player.accessingBank()) {
			player.setSuspiciousPlayer(true, "bank handler packet player not accessing bank");
			player.resetBank();
			return;
		}

		//Make sure the opcode is valid
		final OpcodeIn opcode = OpcodeIn.get(packet.getID());
		if (opcode == null)
			return;

		//These variables are set from packet values
		int catalogID, amount, presetSlot;
		boolean wantsNotes = false;

		switch (opcode) {
			case BANK_CLOSE:
				player.resetBank();
				break;
			case BANK_WITHDRAW:
				catalogID = packet.readShort();
				amount = packet.readInt();

				if (catalogID < 0 || catalogID >= player.getWorld().getServer().getEntityHandler().getItemCount()) {
					return;
				}

				if (player.getConfig().WANT_BANK_NOTES)
					wantsNotes = packet.readByte() == 1;

				amount = Math.min(player.getBank().countId(catalogID), amount);
				player.getBank().withdrawItemToInventory(catalogID, amount, wantsNotes);
				break;
			case BANK_DEPOSIT:
				catalogID = packet.readShort();
				amount = packet.readInt();

				if (catalogID < 0 || catalogID >= player.getWorld().getServer().getEntityHandler().getItemCount()) {
					return;
				}

				amount = Math.min(player.getCarriedItems().getInventory().countId(catalogID), amount);
				player.getBank().depositItemFromInventory(catalogID, amount, true);
				break;
			case BANK_DEPOSIT_ALL_FROM_INVENTORY:
				player.getBank().depositAllFromInventory();
				break;
			case BANK_DEPOSIT_ALL_FROM_EQUIPMENT:
				if (!player.getConfig().WANT_EQUIPMENT_TAB) {
					player.setSuspiciousPlayer(true, "bank deposit from equipment on authentic world");
					return;
				}
				player.getBank().depositAllFromEquipment();
				break;
			case BANK_LOAD_PRESET:
				if (!player.getConfig().WANT_EQUIPMENT_TAB) {
					player.setSuspiciousPlayer(true, "bank load preset on authentic world");
					return;
				}
				if (System.currentTimeMillis() - player.getLastExchangeTime() < 2000) {
					player.message("You are acting too quickly, please wait 2 seconds between actions");
					return;
				}
				presetSlot = packet.readShort();
				if (presetSlot < 0 || presetSlot >= BankPreset.PRESET_COUNT) {
					player.setSuspiciousPlayer(true, "packet seven bank preset slot < 0 or preset slot >= preset count");
					return;
				}
				player.setLastExchangeTime();
				player.getBank().getBankPreset(presetSlot).attemptPresetLoadout();
				break;
			case BANK_SAVE_PRESET:
				if (!player.getConfig().WANT_EQUIPMENT_TAB) {
					player.setSuspiciousPlayer(true, "bank save preset on authentic world");
					return;
				}
				presetSlot = packet.readShort();
				if (presetSlot < 0 || presetSlot >= BankPreset.PRESET_COUNT) {
					player.setSuspiciousPlayer(true, "packet six bank preset slot < 0 or preset slot >= preset count");
					return;
				}
				for (int k = 0; k < Inventory.MAX_SIZE; k++) {
					if (k < player.getCarriedItems().getInventory().size()) {
						Item inventoryItem = player.getCarriedItems().getInventory().get(k);
						player.getBank().getBankPreset(presetSlot).getInventory()[k] = new Item(
							inventoryItem.getCatalogId(), inventoryItem.getAmount(), inventoryItem.getNoted()
						);
					}
					else
						player.getBank().getBankPreset(presetSlot).getInventory()[k] = new Item(ItemId.NOTHING.id(),0);
				}
				for (int k = 0; k < Equipment.SLOT_COUNT; k++) {
					Item equipmentItem = player.getCarriedItems().getEquipment().get(k);
					if (equipmentItem != null) {
						player.getBank().getBankPreset(presetSlot).getEquipment()[k] = new Item(
							equipmentItem.getCatalogId(), equipmentItem.getAmount()
						);
					}
					else
						player.getBank().getBankPreset(presetSlot).getEquipment()[k] = new Item(ItemId.NOTHING.id(),0);
				}
				break;
			default:
				return;
		}
	}
}
