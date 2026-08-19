package com.devpilot.skill;

import com.devpilot.skill.model.SkillPackage;

import java.util.List;

/**
 * Where installable skills come from.
 *
 * <p>Defined as a capability so the marketplace can be a remote registry, a local directory or an
 * internal mirror without anything above it changing. A source only offers packages; deciding to
 * install one is always a separate, human action.
 */
public interface SkillSource {

    /**
     * Lists what the marketplace offers.
     *
     * @return available packages
     * @throws SkillSourceException when the catalogue cannot be read
     */
    List<SkillPackage> catalogue();

    /**
     * Fetches one package.
     *
     * @param skillKey package identifier
     * @return the package
     * @throws SkillSourceException when the package cannot be read
     */
    SkillPackage fetch(String skillKey);

    /** @return where this source reads from, recorded on every installed skill */
    String origin();
}
