package com.guapi_exe.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.guapi_exe.ResoureExporterMod;

@Mod(ResoureExporterMod.MOD_ID)
public final class ResoureExporterModForge {
    public ResoureExporterModForge() {
        EventBuses.registerModEventBus(ResoureExporterMod.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        ResoureExporterMod.init();
    }
}
