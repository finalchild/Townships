package multitallented.redcastlemedia.bukkit.townships.commands;

import org.bukkit.command.CommandSender;

import multitallented.redcastlemedia.bukkit.townships.Townships;

public interface TSCommand {

	public boolean onCommand(CommandSender sender, String[] args, Townships instance);
	
}
