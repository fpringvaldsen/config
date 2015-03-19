package com.typesafe.config.impl;

import com.typesafe.config.ConfigException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class ConfigNodeObject extends ConfigNodeComplexValue {
    ConfigNodeObject(Collection<AbstractConfigNode> children) {
        super(children);
    }

    protected ConfigNodeObject changeValueOnPath(Path desiredPath, AbstractConfigNodeValue value) {
        ArrayList<AbstractConfigNode> childrenCopy = new ArrayList(super.children);
        // Copy the value so we can change it to null but not modify the original parameter
        AbstractConfigNodeValue valueCopy = value;
        for (int i = super.children.size() - 1; i >= 0; i--) {
            if (!(super.children.get(i) instanceof ConfigNodeField)) {
                continue;
            }
            ConfigNodeField node = (ConfigNodeField)super.children.get(i);
            Path key = node.path().value();
            if (key.equals(desiredPath)) {
                if (valueCopy == null)
                    childrenCopy.remove(i);
                else {
                    childrenCopy.set(i, node.replaceValue(value));
                    valueCopy = null;
                }
            } else if (desiredPath.startsWith(key)) {
                if (node.value() instanceof ConfigNodeObject) {
                    Path remainingPath = desiredPath.subPath(key.length());
                    childrenCopy.set(i, node.replaceValue(((ConfigNodeObject)node.value()).changeValueOnPath(remainingPath, valueCopy)));
                    if (valueCopy != null && node.render() != super.children.get(i).render())
                        valueCopy = null;
                }
            }
        }
        return new ConfigNodeObject(childrenCopy);
    }

    public ConfigNodeObject setValueOnPath(String desiredPath, AbstractConfigNodeValue value) {
        ConfigNodePath path = PathParser.parsePathNode(desiredPath);
        return setValueOnPath(path, value);
    }

    private ConfigNodeObject setValueOnPath(ConfigNodePath desiredPath, AbstractConfigNodeValue value) {
        ConfigNodeObject node = changeValueOnPath(desiredPath.value(), value);

        // If the desired Path did not exist, add it
        if (node.render().equals(render())) {
//            boolean startsWithBrace = super.children.get(0) instanceof ConfigNodeSingleToken &&
//                                        ((ConfigNodeSingleToken) super.children.get(0)).token() == Tokens.OPEN_CURLY;
//            ArrayList<AbstractConfigNode> childrenCopy = new ArrayList<AbstractConfigNode>(super.children);
//            ArrayList<AbstractConfigNode> newNodes = new ArrayList();
//            newNodes.add(new ConfigNodeSingleToken(Tokens.newLine(null)));
//            if (startsWithBrace)
//                newNodes.add(new ConfigNodeSingleToken(Tokens.newIgnoredWhitespace(null, "\t")));
//            newNodes.add(desiredPath);
//            newNodes.add(new ConfigNodeSingleToken(Tokens.newIgnoredWhitespace(null, " ")));
//            newNodes.add(new ConfigNodeSingleToken(Tokens.COLON));
//            newNodes.add(new ConfigNodeSingleToken(Tokens.newIgnoredWhitespace(null, " ")));
//            newNodes.add(value);
//            newNodes.add(new ConfigNodeSingleToken(Tokens.newLine(null)));
//
//            if (startsWithBrace) {
//                for (int i = childrenCopy.size() - 1; i >= 0; i--) {
//                    if (childrenCopy.get(i) instanceof ConfigNodeSingleToken &&
//                            ((ConfigNodeSingleToken) childrenCopy.get(i)).token == Tokens.CLOSE_CURLY) {
//                        childrenCopy.add(i, new ConfigNodeField(newNodes));
//                        return new ConfigNodeObject(childrenCopy);
//                    }
//                }
//                throw new ConfigException.BugOrBroken("Object had an opening brace, but no closing brace");
//            } else {
//                childrenCopy.add(new ConfigNodeField(newNodes));
//                node = new ConfigNodeObject(childrenCopy);
//            }
            return addValueOnPath(desiredPath, value);
        }
        return node;
    }

    protected ConfigNodeObject addValueOnPath(ConfigNodePath desiredPath, AbstractConfigNodeValue value) {
        Path path = desiredPath.value();
        ArrayList<AbstractConfigNode> childrenCopy = new ArrayList(super.children);
        if (path.length() > 1) {
            for (int i = super.children.size() - 1; i >= 0; i--) {
                if (!(super.children.get(i) instanceof ConfigNodeField)) {
                    continue;
                }
                ConfigNodeField node = (ConfigNodeField) super.children.get(i);
                Path key = node.path().value();
                if (path.startsWith(key) && node.value() instanceof ConfigNodeObject) {
                    ConfigNodePath remainingPath = desiredPath.subPath(key.length());
                    ConfigNodeObject newValue = (ConfigNodeObject) node.value();
                    childrenCopy.set(i, node.replaceValue(newValue.addValueOnPath(remainingPath, value)));
                    return new ConfigNodeObject(childrenCopy);
                }
            }
        }
        boolean startsWithBrace = super.children.get(0) instanceof ConfigNodeSingleToken &&
                ((ConfigNodeSingleToken) super.children.get(0)).token() == Tokens.OPEN_CURLY;
        ArrayList<AbstractConfigNode> newNodes = new ArrayList();
        newNodes.add(new ConfigNodeSingleToken(Tokens.newLine(null)));
        newNodes.add(desiredPath.first());
        newNodes.add(new ConfigNodeSingleToken(Tokens.newIgnoredWhitespace(null, " ")));
        newNodes.add(new ConfigNodeSingleToken(Tokens.COLON));
        newNodes.add(new ConfigNodeSingleToken(Tokens.newIgnoredWhitespace(null, " ")));

        if (path.length() == 1) {
            newNodes.add(value);
        } else {
            ArrayList<AbstractConfigNode> newObjectNodes = new ArrayList();
            newObjectNodes.add(new ConfigNodeSingleToken(Tokens.OPEN_CURLY));
            newObjectNodes.add(new ConfigNodeSingleToken(Tokens.CLOSE_CURLY));
            ConfigNodeObject newObject = new ConfigNodeObject(newObjectNodes);
            newNodes.add(newObject.addValueOnPath(desiredPath.subPath(1), value));
        }
        newNodes.add(new ConfigNodeSingleToken(Tokens.newLine(null)));

        if (startsWithBrace) {
            for (int i = childrenCopy.size() - 1; i >= 0; i--) {
                if (childrenCopy.get(i) instanceof ConfigNodeSingleToken &&
                        ((ConfigNodeSingleToken) childrenCopy.get(i)).token == Tokens.CLOSE_CURLY) {
                    childrenCopy.add(i, new ConfigNodeField(newNodes));
                    return new ConfigNodeObject(childrenCopy);
                }
            }
            throw new ConfigException.BugOrBroken("Object had an opening brace, but no closing brace");
        } else {
            childrenCopy.add(new ConfigNodeField(newNodes));
            return new ConfigNodeObject(childrenCopy);
        }
    }

    public ConfigNodeComplexValue removeValueOnPath(String desiredPath) {
        Path path = PathParser.parsePath(desiredPath);
        return changeValueOnPath(path, null);
    }
}
