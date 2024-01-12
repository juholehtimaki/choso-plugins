package com.theplug.PWoodcutter;

import com.theplug.DontObfuscate;

@DontObfuscate
public enum Tree {

    Regular("Tree"),
    Oak("Oak tree"),
    Willow("Willow tree"),
    Teak("Teak tree"),
    Maple("Maple tree"),
    Mahogany("Mahogany tree"),
    Yew("Yew tree"),
    Magic("Magic tree");


    final String tree;

    Tree(String tree) {
        this.tree = tree;
    }

    @Override
    public String toString() {
        return this.tree;
    }
}
