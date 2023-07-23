package com.jfrog.mavendeptree.dependenciesresults;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author yahavi
 */
@Getter
@Setter
@NoArgsConstructor
public class MavenDependencyNode {
    private final Set<String> children = new HashSet<>();
    // The Maven scopes such as compile, runtime, test, etc. We use the same naming convention as in the gradle-dep-tree.
    private final Set<String> configurations = new HashSet<>();

    public MavenDependencyNode(String configuration) {
        if (configuration != null) {
            this.configurations.add(configuration);
        }
    }

    public void addChild(String childName) {
        children.add(childName);
    }

    public void addConfiguration(String configuration) {
        configurations.add(configuration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(children, configurations);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        MavenDependencyNode other = (MavenDependencyNode) obj;
        return children.equals(other.children) && configurations.equals(other.configurations);
    }
}
