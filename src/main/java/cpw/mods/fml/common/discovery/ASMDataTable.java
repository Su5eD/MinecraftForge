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

package cpw.mods.fml.common.discovery;

import com.google.common.base.Predicate;
import com.google.common.collect.*;
import cpw.mods.fml.common.ModContainer;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ASMDataTable
{
    public static class ASMData
    {
        private ModCandidate candidate;
        private String annotationName;
        private String className;
        private String objectName;
        private Map<String,Object> annotationInfo;
        public ASMData(ModCandidate candidate, String annotationName, String className, String objectName, Map<String,Object> info)
        {
            this.candidate = candidate;
            this.annotationName = annotationName;
            this.className = className;
            this.objectName = objectName;
            this.annotationInfo = info;
        }
        public ModCandidate getCandidate()
        {
            return candidate;
        }
        public String getAnnotationName()
        {
            return annotationName;
        }
        public String getClassName()
        {
            return className;
        }
        public String getObjectName()
        {
            return objectName;
        }
        public Map<String, Object> getAnnotationInfo()
        {
            return annotationInfo;
        }
    }

    private static class ModContainerPredicate implements Predicate<ASMData>
    {
        private ModContainer container;
        public ModContainerPredicate(ModContainer container)
        {
            this.container = container;
        }
        public boolean apply(ASMData data)
        {
            return container.getSource().equals(data.candidate.getModContainer());
        }
    }
    private SetMultimap<String, ASMData> globalAnnotationData = HashMultimap.create();
    private Map<ModContainer, SetMultimap<String,ASMData>> containerAnnotationData;

    private List<ModContainer> containers = Lists.newArrayList();

    public SetMultimap<String,ASMData> getAnnotationsFor(ModContainer container)
    {
        if (containerAnnotationData == null)
        {
            ImmutableMap.Builder<ModContainer, SetMultimap<String, ASMData>> mapBuilder = ImmutableMap.<ModContainer, SetMultimap<String,ASMData>>builder();
            for (ModContainer cont : containers)
            {
                Multimap<String, ASMData> values = Multimaps.filterValues(globalAnnotationData, new ModContainerPredicate(cont));
                mapBuilder.put(cont, ImmutableSetMultimap.copyOf(values));
            }
            containerAnnotationData = mapBuilder.build();
        }
        return containerAnnotationData.get(container);
    }

    public Set<ASMData> getAll(String annotation)
    {
        return globalAnnotationData.get(annotation);
    }

    public void addASMData(ModCandidate candidate, String annotation, String className, String objectName, Map<String,Object> annotationInfo)
    {
        globalAnnotationData.put(annotation, new ASMData(candidate, annotation, className, objectName, annotationInfo));
    }

    public void addContainer(ModContainer container)
    {
        this.containers.add(container);
    }
}
