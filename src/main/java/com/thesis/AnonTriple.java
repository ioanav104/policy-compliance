package com.thesis;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;

public class AnonTriple {
	public Triple triple;
	public Node node;
	public AnonTriple(Triple t, Node n) {
		this.triple = t;
		this.node = n;
	}
	public String toString() {
		return "(" + triple.toString()+" ," + node.toString()+")";
	}
}