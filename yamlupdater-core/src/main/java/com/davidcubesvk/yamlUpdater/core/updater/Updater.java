package com.davidcubesvk.yamlUpdater.core.updater;

import com.davidcubesvk.yamlUpdater.core.block.Section;
import com.davidcubesvk.yamlUpdater.core.path.Path;
import com.davidcubesvk.yamlUpdater.core.settings.updater.UpdaterSettings;
import com.davidcubesvk.yamlUpdater.core.versioning.Version;
import com.davidcubesvk.yamlUpdater.core.versioning.wrapper.Versioning;

import java.util.Map;
import java.util.Objects;

/**
 * Updater class responsible for executing the whole process:
 * <ol>
 *     <li>loading file version IDs</li>
 *     <li>comparing IDs (to check if updating, downgrading...)</li>
 *     <li>marking force copy blocks in the user section</li>
 *     <li>applying relocations to the user section (if the files are not the same version ID) - see {@link Relocator#apply(Map)}</li>
 *     <li>merging both files - see {@link Merger#merge(Section, Section, UpdaterSettings)}</li>
 * </ol>
 */
public class Updater {

    /**
     * Updater instance for calling non-static methods.
     */
    private static final Updater UPDATER = new Updater();

    /**
     * Updates the given user section using the given default equivalent and settings; with the result reflected in the
     * user section given. The process consists of:
     * <ol>
     *     <li>loading file version IDs,</li>
     *     <li>comparing IDs (to check if updating, downgrading...),</li>
     *     <li>marking force copy blocks in the user section,</li>
     *     <li>applying relocations to the user section (if the files are not the same version ID) - see {@link Relocator#apply(Map)}),</li>
     *     <li>merging both files - see {@link Merger#merge(Section, Section, UpdaterSettings)}.</li>
     * </ol>
     *
     * @param userSection the user section to update
     * @param defSection  section equivalent in the default file (to update against)
     * @param settings    the settings
     */
    public static void update(Section userSection, Section defSection, UpdaterSettings settings) {
        //Apply versioning stuff
        UPDATER.runVersionDependent(userSection, defSection, settings);
        //Merge
        Merger.merge(userSection, defSection, settings);
        //If auto save is enabled
        if (settings.isAutoSave())
            userSection.getRoot().save();
    }

    /**
     * Runs version-dependent mechanics.
     * <ol>
     *     <li>If {@link UpdaterSettings#getVersioning()} is <code>null</code>, does not proceed.</li>
     *     <li>If the version of the user (section, file) is not provided (is <code>null</code>;
     *     {@link Versioning#getUserFileId(Section)}), assigns the oldest version specified by the underlying pattern
     *     (see {@link Versioning#getOldest()}). If provided, marks all blocks which have force copy option enabled
     *     (determined by the set of paths, see {@link UpdaterSettings#getForceCopy()}).</li>
     *     <li>If downgrading and it is enabled, does not proceed further. If disabled, throws an
     *     {@link UnsupportedOperationException}.</li>
     *     <li>If version IDs equal, does not proceed as well.</li>
     *     <li>Applies all relocations needed.</li>
     * </ol>
     *
     * @param userSection    the user section
     * @param defaultSection the default section equivalent
     * @param settings       updater settings to use
     */
    private void runVersionDependent(Section userSection, Section defaultSection, UpdaterSettings settings) {
        //Versioning
        Versioning versioning = settings.getVersioning();
        //If the versioning is not set
        if (versioning == null)
            return;

        //Versions
        Version user = versioning.getUserFileId(userSection), def = versioning.getDefaultFileId(defaultSection);
        //Check default file version
        Objects.requireNonNull(def, "Version ID of the default file cannot be null!");
        //If user ID is not null
        if (user != null) {
            //Go through all force copy paths
            for (Path path : settings.getForceCopy().get(user.asID()))
                //Set
                userSection.getBlockSafe(path).ifPresent(block -> block.setForceCopy(true));
        } else {
            //Set to oldest (to go through all relocations supplied)
            user = versioning.getOldest();
        }

        //Compare
        int compared = user.compareTo(def);
        //If downgrading
        if (compared > 0) {
            //If enabled
            if (settings.isEnableDowngrading())
                return;

            //Throw an error
            throw new UnsupportedOperationException(String.format("Downgrading is not enabled (%s > %s)!", def.asID(), user.asID()));
        }

        //No relocating needed
        if (compared == 0)
            return;

        //Initialize relocator
        Relocator relocator = new Relocator(userSection, user, def);
        //Apply all
        relocator.apply(settings.getRelocations());
    }

}