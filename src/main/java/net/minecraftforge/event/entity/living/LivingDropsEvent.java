package net.minecraftforge.event.entity.living;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.Cancelable;

import java.util.ArrayList;

@Cancelable
public class LivingDropsEvent extends LivingEvent
{
    public final DamageSource source;
    public final ArrayList<EntityItem> drops;
    public final int lootingLevel;
    public final boolean recentlyHit;
    public final int specialDropValue;
    
    public LivingDropsEvent(EntityLiving entity, DamageSource source, ArrayList<EntityItem> drops, int lootingLevel, boolean recentlyHit, int specialDropValue)
    {
        super(entity);
        this.source = source;
        this.drops = drops;
        this.lootingLevel = lootingLevel;
        this.recentlyHit = recentlyHit;
        this.specialDropValue = specialDropValue;
    }
}
