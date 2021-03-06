package br.com.gamemods.minecity.forge.base.accessors.entity.passive;

import br.com.gamemods.minecity.api.permission.PermissionFlag;
import br.com.gamemods.minecity.forge.base.MineCityForge;
import br.com.gamemods.minecity.forge.base.accessors.item.IItemStack;
import br.com.gamemods.minecity.forge.base.command.ForgePlayer;
import br.com.gamemods.minecity.forge.base.core.Referenced;
import br.com.gamemods.minecity.forge.base.core.transformer.forge.ForgeInterfaceTransformer;
import br.com.gamemods.minecity.forge.base.protection.reaction.NoReaction;
import br.com.gamemods.minecity.forge.base.protection.reaction.Reaction;
import br.com.gamemods.minecity.forge.base.protection.reaction.SingleBlockReaction;
import br.com.gamemods.minecity.forge.base.protection.reaction.TriggeredReaction;
import net.minecraft.entity.passive.EntitySheep;

@Referenced(at = ForgeInterfaceTransformer.class)
public interface IEntitySheep extends IEntityAnimal
{
    @Override
    default EntitySheep getForgeEntity()
    {
        return (EntitySheep) this;
    }

    default boolean isSheared()
    {
        return getForgeEntity().getSheared();
    }

    @Override
    default Reaction reactPlayerInteractLiving(ForgePlayer<?, ?, ?> player, IItemStack stack, boolean offHand)
    {
        if(stack != null && !isChild())
        {
            if(!isSheared() && stack.getIItem().getUnlocalizedName().equals("item.shears"))
            {
                MineCityForge server = player.getServer();
                TriggeredReaction react = new SingleBlockReaction(getBlockPos(player.getServer()), PermissionFlag.HARVEST);
                react.addAllowListener((reaction, permissible, flag, pos, message) ->
                    server.consumeItemsOrAddOwnerIf(getEntityPos(server), 2, 1, 2, null, player.identity(), item->
                            item.getStack().getIItem().isHarvest(item.getStack())
                    )
                );
                return react;
            }

            if(isBreedingItem(stack))
                return new SingleBlockReaction(getBlockPos(player.getServer()), PermissionFlag.PVC);
        }

        return NoReaction.INSTANCE;
    }
}
