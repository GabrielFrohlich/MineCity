package br.com.gamemods.minecity.forge.mc_1_7_10.command;

import br.com.gamemods.minecity.api.command.LegacyFormat;
import br.com.gamemods.minecity.api.command.Message;
import br.com.gamemods.minecity.forge.base.command.ForgePlayer;
import br.com.gamemods.minecity.forge.mc_1_7_10.MineCitySeven;
import br.com.gamemods.minecity.forge.mc_1_7_10.protection.SevenMovementListener;
import br.com.gamemods.minecity.forge.mc_1_7_10.protection.SevenMovementMonitor;
import br.com.gamemods.minecity.protection.MovementMonitor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;

public class SevenPlayer extends ForgePlayer<MineCitySeven, EntityPlayerMP, SevenPlayerSender, Entity>
        implements SevenMovementListener
{
    public SevenPlayer(MineCitySeven mod, EntityPlayerMP player)
    {
        super(new SevenPlayerSender(mod, player));
    }

    @Override
    protected MovementMonitor<Entity, MineCitySeven> createMonitor()
    {
        return new SevenMovementMonitor(mod, player, mod.block(player), this);
    }

    @Override
    public void sendTitle(Message title, Message subtitle)
    {
        if(subtitle == null)
            send(new Message("",LegacyFormat.DARK_GRAY+" ~ "+LegacyFormat.GRAY+"${name}", new Object[]{"name", title}));
        else
            send(new Message("",LegacyFormat.DARK_GRAY+" ~ ${title} :"+LegacyFormat.GRAY+" ${sub}", new Object[][]{
                    {"sub", subtitle},
                    {"title", title}
            }));
    }

    @Override
    public boolean kick(Message message)
    {
        cmd.sender.playerNetServerHandler.kickPlayerFromServer(cmd.mod.transformer.toLegacy(message));
        return true;
    }
}