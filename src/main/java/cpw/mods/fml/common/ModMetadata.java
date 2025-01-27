/*
 * The FML Forge Mod Loader suite.
 * Copyright (C) 2012 cpw
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this library; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

package cpw.mods.fml.common;

import argo.jdom.JsonNode;
import argo.jdom.JsonStringNode;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import cpw.mods.fml.common.functions.ModNameFunction;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import cpw.mods.fml.common.versioning.VersionParser;

import java.util.*;
import java.util.logging.Level;

import static argo.jdom.JsonNodeBuilders.aStringBuilder;

/**
 * @author cpw
 *
 */
public class ModMetadata
{
    private static final class JsonStringConverter implements Function<JsonNode, Object>
    {
        public Object apply(JsonNode arg0)
        {
            if (arg0.hasElements())
            {
                return Lists.transform(arg0.getElements(), new JsonArrayConverter());
            }
            else
            {
                return arg0.getText();
            }
        }
    }

    private static final class JsonArrayConverter implements Function<JsonNode, String>
    {
        public String apply(JsonNode arg0)
        {
            return arg0.getText();
        }
    }

    public String modId;
    public String name;
    public String description;

    public String url = "";
    public String updateUrl = "";

    public String logoFile = "";
    public String version = "";
    public List<String> authorList = Lists.newArrayList();
    public String credits = "";
    public String parent = "";
    public String[] screenshots;

    public ModContainer parentMod;
    public List<ModContainer> childMods = Lists.newArrayList();

    public boolean useDependencyInformation;
    public Set<ArtifactVersion> requiredMods;
    public List<ArtifactVersion> dependencies;
    public List<ArtifactVersion> dependants;
    public boolean autogenerated;

    public ModMetadata(JsonNode node)
    {
        Map<JsonStringNode, Object> processedFields = Maps.transformValues(node.getFields(), new JsonStringConverter());
        modId = (String)processedFields.get(aStringBuilder("modid"));
        if (Strings.isNullOrEmpty(modId))
        {
            FMLLog.log(Level.SEVERE, "Found an invalid mod metadata file - missing modid");
            throw new LoaderException();
        }
        name = Strings.nullToEmpty((String)processedFields.get(aStringBuilder("name")));
        description = Strings.nullToEmpty((String)processedFields.get(aStringBuilder("description")));
        url = Strings.nullToEmpty((String)processedFields.get(aStringBuilder("url")));
        updateUrl = Strings.nullToEmpty((String)processedFields.get(aStringBuilder("updateUrl")));
        logoFile = Strings.nullToEmpty((String)processedFields.get(aStringBuilder("logoFile")));
        version = Strings.nullToEmpty((String)processedFields.get(aStringBuilder("version")));
        credits = Strings.nullToEmpty((String)processedFields.get(aStringBuilder("credits")));
        parent =  Strings.nullToEmpty((String)processedFields.get(aStringBuilder("parent")));
        authorList = Objects.firstNonNull(((List<String>)processedFields.get(aStringBuilder("authors"))),Objects.firstNonNull(((List<String>)processedFields.get(aStringBuilder("authorList"))), authorList));
        requiredMods = processReferences(processedFields.get(aStringBuilder("requiredMods")), HashSet.class);
        dependencies = processReferences(processedFields.get(aStringBuilder("dependencies")), ArrayList.class);
        dependants = processReferences(processedFields.get(aStringBuilder("dependants")), ArrayList.class);
        useDependencyInformation = Boolean.parseBoolean(Strings.nullToEmpty((String)processedFields.get(aStringBuilder("useDependencyInformation"))));
    }

    public ModMetadata()
    {
    }

    private <T extends Collection<ArtifactVersion>> T processReferences(Object refs, Class<? extends T> retType)
    {
        T res = null;
        try
        {
            res = retType.newInstance();
        }
        catch (Exception e)
        {
            // unpossible
        }

        if (refs == null)
        {
            return res;
        }
        for (String ref : ((List<String>)refs))
        {
            res.add(VersionParser.parseVersionReference(ref));
        }
        return res;
    }

    public String getChildModCountString()
    {
        return String.format("%d child mod%s", childMods.size(), childMods.size() != 1 ? "s" : "");
    }

    public String getAuthorList()
    {
        return Joiner.on(", ").join(authorList);
    }

    public String getChildModList()
    {
        return Joiner.on(", ").join(Lists.transform(childMods, new ModNameFunction()));
    }

    public String printableSortingRules()
    {
        return "";
    }
}
