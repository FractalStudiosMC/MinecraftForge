/*
 * Minecraft Forge
 * Copyright (c) 2016-2019.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.fml.loading.moddiscovery;

import cpw.mods.modlauncher.ServiceLoaderStreamUtils;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.ModSorter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import static net.minecraftforge.fml.loading.LogMarkers.SCAN;


public class ModDiscoverer {
    private static final Logger LOGGER = LogManager.getLogger();
    private final ServiceLoader<IModLocator> locators;
    private final List<IModLocator> locatorList;

    public ModDiscoverer(Map<String, ?> arguments) {
        locators = ServiceLoader.load(IModLocator.class);
        locatorList = ServiceLoaderStreamUtils.toList(this.locators);
        locatorList.forEach(l->l.initArguments(arguments));
        LOGGER.debug(SCAN,"Found Mod Locators : {}", ()->locatorList.stream().map(iModLocator -> "("+iModLocator.name() + ":" + iModLocator.getClass().getPackage().getImplementationVersion()+")").collect(Collectors.joining(",")));
    }

    ModDiscoverer(List<IModLocator> locatorList) {
        this.locatorList = locatorList;
        this.locators = null;
    }

    public BackgroundScanHandler discoverMods() {
        LOGGER.debug(SCAN,"Scanning for mods and other resources to load. We know {} ways to find mods", locatorList.size());
        final Map<ModFile.Type, List<ModFile>> modFiles = locatorList.stream()
                .peek(loc -> LOGGER.debug(SCAN,"Trying locator {}", loc))
                .map(IModLocator::scanMods)
                .flatMap(Collection::stream)
                .peek(mf -> LOGGER.debug(SCAN,"Found mod file {} of type {} with locator {}", mf.getFileName(), mf.getType(), mf.getLocator()))
                .collect(Collectors.groupingBy(ModFile::getType));

        FMLLoader.getLanguageLoadingProvider().addAdditionalLanguages(modFiles.get(ModFile.Type.LANGPROVIDER));
        BackgroundScanHandler backgroundScanHandler = new BackgroundScanHandler();
        final List<ModFile> mods = modFiles.getOrDefault(ModFile.Type.MOD, Collections.emptyList());
        final List<ModFile> brokenFiles = new ArrayList<>();
        for (Iterator<ModFile> iterator = mods.iterator(); iterator.hasNext(); )
        {
            ModFile mod = iterator.next();
            if (!mod.getLocator().isValid(mod) || !mod.identifyMods()) {
                LOGGER.warn(SCAN, "File {} has been ignored - it is invalid", mod.getFilePath());
                iterator.remove();
                brokenFiles.add(mod);
            }
        }
        LOGGER.debug(SCAN,"Found {} mod files with {} mods", mods::size, ()->mods.stream().mapToInt(mf -> mf.getModInfos().size()).sum());
        final LoadingModList loadingModList = ModSorter.sort(mods);
        loadingModList.addCoreMods();
        loadingModList.addAccessTransformers();
        loadingModList.addForScanning(backgroundScanHandler);
        loadingModList.setBrokenFiles(brokenFiles);
        return backgroundScanHandler;
    }

    public void addExplodedTarget(final Path compiledClasses, final Path forgemodstoml) {

    }
}
