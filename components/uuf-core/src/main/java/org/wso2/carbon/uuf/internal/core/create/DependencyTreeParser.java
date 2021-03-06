/*
 *  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.uuf.internal.core.create;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.wso2.carbon.uuf.exception.MalformedConfigurationException;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class DependencyTreeParser {

    public static Result parse(List<String> dependencyTreeLines) {
        // Flattened dependencies map.
        // key   = component name
        // value = all dependencies of the 'key'
        SetMultimap<String, String> flattenedDependencies = HashMultimap.create();
        // Leveled dependencies list.
        // index       = dependency level, index 0 == root component's dependencies
        // List.get(i) = set of dependencies in level i
        List<Set<ComponentData>> leveledDependencies = new ArrayList<>(6);

        int previousLevel = 0;
        String previousComponentName = null;
        Deque<Pair<String, List<String>>> parentNodesStack = new LinkedList<>();

        for (int i = 0; i < dependencyTreeLines.size(); i++) {
            String line = dependencyTreeLines.get(i);
            int level = countLevel(line);
            int jump = (level - previousLevel);
            ComponentData currentComponent = getComponentData(line);

            if (level < leveledDependencies.size()) {
                leveledDependencies.get(level).add(currentComponent);
            } else {
                Set<ComponentData> set = new HashSet<>();
                set.add(currentComponent);
                leveledDependencies.add(level, set);
            }

            if (i == 0) {
                // Very first leaf dependency.
                previousComponentName = currentComponent.name;
                continue;
            }
            if (jump < 0) {
                // Dependency level decreased, so remove entries from the stack.
                for (int j = Math.abs(jump); j > 0; j--) {
                    Pair<String, List<String>> entry = parentNodesStack.removeLast();
                    flattenedDependencies.putAll(entry.getKey(), entry.getValue());
                }
            } else if (jump > 0) { // jump == 1
                // Dependency level increased, so add an entry to the stack.
                parentNodesStack.add(new ImmutablePair<>(previousComponentName, new ArrayList<>(3)));
            }
            // (jump == 0): Same dependency level, no need to change the stack.

            // Add current component name to all parent nodes as a dependency.
            for (Pair<String, List<String>> entry : parentNodesStack) {
                entry.getValue().add(currentComponent.name);
            }

            previousLevel = level;
            previousComponentName = currentComponent.name;
        }
        // If there is any remaining stack elements, add them to the flattenedDependencies.
        for (Pair<String, List<String>> entry : parentNodesStack) {
            flattenedDependencies.putAll(entry.getKey(), entry.getValue());
        }

        return new Result(flattenedDependencies, leveledDependencies);
    }

    private static int countLevel(String line) {
        int indent = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '+' || c == ' ' || c == '\\' || c == '|') {
                indent++;
            } else {
                break;
            }
        }
        return (indent <= 1) ? indent : (indent / 2);
    }

    private static ComponentData getComponentData(String dependencyLine) {
        /* dependencyLine string should be in one of following formats.
         *  <group ID>:<artifact ID>:<artifact type>:<artifact version>
         *  <group ID>:<artifact ID>:<artifact type>:<artifact version>:compile
         *  (<group ID>:<artifact ID>:<artifact type>:<artifact version>:compile - omitted for duplicate)
         */
        String[] parts = dependencyLine.split(":");
        switch (parts.length) {
            case 4:
            case 5:
                return new ComponentData(parts[1], parts[3]);
            case 6:
                return new ComponentData(parts[1], parts[4]);
            default:
                throw new MalformedConfigurationException(
                        "Format of the dependency line '" + dependencyLine + "' is incorrect. Found " + parts.length +
                                " instead of 4, 5 or 6");
        }
        // component name = <artifact ID> (2nd part), component version = <artifact version> (4th part)
    }

    public static class Result {

        private final SetMultimap<String, String> flattenedDependencies;
        private final List<Set<ComponentData>> leveledDependencies;

        public Result(SetMultimap<String, String> flattenedDependencies,
                      List<Set<ComponentData>> leveledDependencies) {
            this.flattenedDependencies = flattenedDependencies;
            this.leveledDependencies = leveledDependencies;
        }

        public SetMultimap<String, String> getFlattenedDependencies() {
            return flattenedDependencies;
        }

        public List<Set<ComponentData>> getLeveledDependencies() {
            return leveledDependencies;
        }
    }

    public static class ComponentData {

        private final String name;
        private final String version;

        public ComponentData(String name, String version) {
            this.name = name;
            this.version = version;
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, version);
        }

        @Override
        public boolean equals(Object obj) {
            if ((obj != null) && (obj instanceof ComponentData)) {
                ComponentData other = (ComponentData) obj;
                return Objects.equals(this.name, other.name) && Objects.equals(this.version, other.version);
            }
            return false;
        }
    }
}
