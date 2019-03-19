package com.thesis;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementGroup;
import org.apache.jena.sparql.syntax.ElementPathBlock;
import org.apache.jena.util.FileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class PrivacyCompliance 
{
    @SuppressWarnings( value = "unused" )
    private static final Logger log = LoggerFactory.getLogger(PrivacyCompliance.class);
    private static Model model = ModelFactory.createDefaultModel();
    private static Query query;
    
    public PrivacyCompliance(Model m, Query q) {
    	model = m;
    	query = q;
    }

    private static Model readModel(String inputFileName) {
        InputStream in = FileManager.get().open( inputFileName );
        Model model = ModelFactory.createDefaultModel();
        if (in == null) {
            throw new IllegalArgumentException( "File: " + inputFileName + " not found");
        }
        
        // read the RDF/XML file
        model.read(in, "");
        try {
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
        return model;
    }
    
    @SuppressWarnings("unused")
	private static void printModel(Model model) {
    	// Used for debugging
        NodeIterator it = model.listObjects();
        while(it.hasNext()) {
      	  System.out.println(it.next().toString());
        }
    }
    
	private static List<Map<String, RDFNode>> generateHomomorphisms(Query query) {
		List<Map<String, RDFNode>> homs = new ArrayList<Map<String, RDFNode>>();
		HashMap<String, RDFNode> h;
	    List<String> vars = query.getResultVars();
		try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
            ResultSet  result = qexec.execSelect();
            while(result.hasNext()) {
            	QuerySolution sol = result.next();
            	h = new HashMap<String,RDFNode>();
            	for(String v : vars) {
            		h.put(v, sol.get(v));
            	}
            	homs.add(h);
            }
            return homs;
        }
	}
	
	private static Set<Triple> getTripleSet(Query query){		
		ElementGroup eg = (ElementGroup) query.getQueryPattern();
		Set<Triple> triples= new HashSet<Triple>();
		for(int i = 0; i< eg.size();i++) {
			Element element = eg.get(i);
			if (element instanceof ElementPathBlock) {
				ElementPathBlock epb = (ElementPathBlock) element;
				Iterator<TriplePath> it = epb.patternElts();
				while(it.hasNext()) {
					Triple triple = it.next().asTriple();
					triples.add(triple);
				}
			}
		}
		return triples;
    }
	
	private static HashMap<String, Integer> VarCount(Set<Triple> triples){
		HashMap<String, Integer> count = new HashMap<String,Integer>();
		String name;
		for(Triple t : triples) {
			if(t.getSubject().isVariable()) {
				name = t.getSubject().getName();
				if(!count.containsKey(name)) {
					count.put(name, 1);
				} else {
					Integer c = count.get(name);
					count.put(name, c+1);
				}
			}
			if(t.getObject().isVariable()) {
				name = t.getObject().getName();
				if(!count.containsKey(name)) {
					count.put(name, 1);
				} else {
					Integer c = count.get(name);
					count.put(name, c+1);
				}
			}
		}
		return count;
		
	}
	
	private static void addNode(Node node, Set<String> answerVars, Triple t, 
			Set<AnonTriple> anonable, Map<String, Integer> varCount) {
		if(node.isVariable()) {
			String name = node.getName();
			if(answerVars.contains(name)) {
				// the node is an answer variable
				anonable.add(new AnonTriple(t, node));
			} else if (varCount.get(name)>1) {
				// the node is an existential variable
				anonable.add(new AnonTriple(t, node));
			}
		} else {
			// the node is a constant
			anonable.add(new AnonTriple(t, node));
		}
	}
	
	private static Set<AnonTriple> Anonymisable(Set<Triple> triples, Set<String> answerVars){
		Set<AnonTriple> anonable = new HashSet<AnonTriple>();
		Map<String, Integer> varCount = VarCount(triples);
		for(Triple t : triples) {
			addNode(t.getSubject(), answerVars, t, anonable, varCount);
			addNode(t.getObject(), answerVars, t, anonable, varCount);
		}
		return anonable;
	}
	
	private static List<Set<AnonTriple>> AnonSet(Set<AnonTriple> anonable, 
			List<Map<String, RDFNode>> homs) {
         List<Set<AnonTriple>> anonSets = new ArrayList<Set<AnonTriple>>();
         for(Map<String, RDFNode> hom : homs) {
        	 HashSet<AnonTriple> anonSet = new HashSet<AnonTriple>();
        	 for(AnonTriple a : anonable) {
    	    	 AnonTriple at = new AnonTriple(a.triple, a.node);
        	     Node s = at.triple.getSubject();
        	     Node o = at.triple.getObject();
        	     if(at.node.isVariable()) {
        	    	 at.node = hom.get(a.node.getName()).asNode();
        	     }

        	     if(s.isVariable()) {
        	    	s = hom.get(s.getName()).asNode();
        	     }
        	     if(o.isVariable()) {
         	    	o = hom.get(o.getName()).asNode();
         	     }
        	     at.triple = new Triple(s, a.triple.getPredicate(), o);
        	     anonSet.add(at);
        	 }
        	 anonSets.add(anonSet);
         }
         return anonSets;
	}
	
	private static void Nullify(Set<AnonTriple> toNullify) {
		for(AnonTriple at : toNullify) {
			Node n = at.node;
			Node s = at.triple.getSubject();
			Node p = at.triple.getPredicate();
			Node o = at.triple.getObject();
			Statement stmt = model.asStatement(at.triple);
			model.remove(stmt);
			Resource blank = model.createResource();
			if(n.equals(s)) {
				model.add(blank, model.getProperty(p.getURI()), model.asRDFNode(o));
			} else if (n.equals(o)) {
				model.add(model.getResource(s.getURI()), model.getProperty(p.getURI()), model.asRDFNode(blank.asNode()));
			}
		}
		
	}
	
	public static void Execute() {

		Query fullQuery = QueryFactory.create(query);
		fullQuery.setQueryResultStar(true);
        Set<Triple> triples = getTripleSet(query);
        Set<String> answerVars = new HashSet<String>(query.getResultVars());
        Set<AnonTriple> anonable = Anonymisable(triples, answerVars);
		List<Map<String, RDFNode>> homs = generateHomomorphisms(fullQuery);
		List<Set<AnonTriple>> anonSet = AnonSet(anonable, homs);
        HittingSetApproximation<AnonTriple> hit = new HittingSetApproximation<AnonTriple>(anonSet);
        Set<AnonTriple> toNullify = hit.Execute();
        Nullify(toNullify);
        System.out.println("Percent of triples containing a blank: "+ (double)(toNullify.size())/(model.size())*100 + "%");
        
	}
	
	public static void main(String[] args) {
        
		String sparqlQuery = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" + 
				"PREFIX ub: <http://www.lehigh.edu/~zhp2/2004/0401/univ-bench.owl#>\n" + 
				"SELECT ?name\n" + 
				"WHERE\n" + 
				"{ ?X ub:name ?name."
				+ "?X rdf:type ub:Publication .\n" + 
				"  ?X ub:publicationAuthor \n" + 
				"        <http://www.Department0.University0.edu/AssistantProfessor0>}";
		if(args.length>0) 
			model = readModel(args[0]);
		else {
			System.out.println("You must specify a model");
		    return;
		}
		if(args.length>1) 
			sparqlQuery = args[1];
		query = QueryFactory.create(sparqlQuery);
		List<Map<String, RDFNode>> homs = generateHomomorphisms(query);
        System.out.println(homs);
		Execute();
        System.out.println("Now the query results are: ");
	    homs = generateHomomorphisms(query);
        System.out.println(homs);
        
	}

}
