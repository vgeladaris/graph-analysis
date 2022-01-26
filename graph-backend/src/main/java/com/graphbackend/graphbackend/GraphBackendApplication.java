package com.graphbackend.graphbackend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@SpringBootApplication
@RestController
public class GraphBackendApplication 
{
	public static void main(String[] args) 
	{
		SpringApplication.run(GraphBackendApplication.class, args);
	}


	@CrossOrigin(origins = "*")
	@PostMapping("/scc")
	public String scc(@RequestBody String csv)
	{
		System.out.println("Calculating critical connections...");

		Graph G = createGraph(csv);
		List<List<String>> criticaConnections = criticalConnections(G);

		System.out.println("Done");

		JSONObject result = new JSONObject();
		JSONArray arr = new JSONArray();
		for(List<String> connection : criticaConnections)
		{
			JSONObject temp = new JSONObject();

			temp.put("vertex1", connection.get(0));
			temp.put("vertex2", connection.get(1));

			arr.put(temp);
		}

		result.put("results", arr);
		return result.toString();
	}

	@CrossOrigin(origins = "*")
	@PostMapping("/detectfraud")
	public String detectFraud(@RequestBody String csv)
	{
		Graph G = createGraph(csv);
		HashMap<String, Float> nodes = detectFraudPR(G);
		HashMap<String, Float> sortedNodes = sortByValue(nodes, false);

		JSONObject obj = new JSONObject(csv);
		String id = obj.getString("showId");

		JSONObject results = new JSONObject();
		JSONArray arr = new JSONArray();
		
		for(var entry : sortedNodes.entrySet())
		{
			JSONObject temp = new JSONObject();
			if(id.equals("id"))
			{
				temp.put(id, entry.getKey());
			}
			else
			{
				temp.put(id, G.infoList.get(entry.getKey()).get(id));
			}
			
			arr.put(temp);
		}

		results.put("results", arr);
		return results.toString();
	}


	@CrossOrigin(origins = "*")
	@PostMapping("/clustering")
	public String clustering(@RequestBody String csv)
	{
		Graph G = createGraph(csv);

		System.out.println("Calculating average clustering coefficient...");

		double averageClusteringCoefficient = -1;
		HashMap<String, Double> scores = new HashMap<String, Double>();
		
		double avg = getAverageClusteringCoefficient(G, averageClusteringCoefficient, scores);

		System.out.println("Done");

		JSONObject results = new JSONObject();
		results.put("results", avg);
		
		return results.toString();
	}


	@CrossOrigin(origins = "*")
	@PostMapping("/predictlink")
	public String predictLink(@RequestBody String csv)
	{
		Graph G = createGraph(csv);

		JSONObject obj = new JSONObject(csv);

		System.out.println("Predicting link...");

		double p = predict(G, obj.getString("link1"), obj.getString("link2"));

		System.out.println("Done");

		JSONObject results = new JSONObject();
		results.put("results", p);
		results.put("link1", obj.getString("link1"));
		results.put("link2", obj.getString("link2"));
		
		return results.toString();
	}


	@CrossOrigin(origins = "*")
	@PostMapping("/density")
	public String density(@RequestBody String csv)
	{		
		Graph G = createGraph(csv);

		System.out.println("Calculating density...");

		JSONObject results = new JSONObject();
		results.put("results", ((float)G.m / (G.n * (G.n - 1))));

		System.out.println("Done");
		
		return results.toString();
	}


	private double predict(Graph G, String u, String v)
    {
        ArrayList<String> gu = G.adjList.get(u);
        ArrayList<String> gv = G.adjList.get(v);

        Set<String> intersection = new HashSet<>(gu);
        intersection.retainAll(gv);

        double result = 0d;
        for (String z : intersection) {
            int dz = G.adjList.get(z).size();
            if (dz < 2) {
				System.out.println(z + " has degree less than 2");
                continue;
            }
            result += 1d / Math.log(dz);
        }
        return result;
    }


	private double getAverageClusteringCoefficient(Graph G, double averageClusteringCoefficient, HashMap<String, Double> scores)
    {
		computeFullScoreMap(G, scores);
		averageClusteringCoefficient = 0;

		for (double value : scores.values())
			averageClusteringCoefficient += value;

		averageClusteringCoefficient /= G.adjList.size();

        return averageClusteringCoefficient;
    }


	private void computeFullScoreMap(Graph G, HashMap<String, Double> scores)
    {
        for (String s : G.adjList.keySet()) 
		{
            if (scores.containsKey(s)) 
			{
                continue;
            }

            scores.put(s, computeLocalClusteringCoefficient(G, s, scores));
        }
    }


	private double computeLocalClusteringCoefficient(Graph G, String s, HashMap<String, Double> scores)
    {
        if (scores.containsKey(s)) {
            return scores.get(s);
        }

        ArrayList<String> neighbourhood = G.adjList.get(s);

        final double k = neighbourhood.size();
        double numberTriplets = 0;

        for (String p : neighbourhood)
            for (String q : neighbourhood)
                if (G.containsEdge(p, q))
                    numberTriplets++;

        if (k <= 1)
            return 0.0;
        else
            return numberTriplets / (k * (k - 1));
    }


	private static HashMap<String, Float> sortByValue(HashMap<String, Float> unsortMap, final boolean order)
    {
        List<Entry<String, Float>> list = new LinkedList<>(unsortMap.entrySet());

        list.sort((o1, o2) -> order ? o1.getValue().compareTo(o2.getValue()) == 0
                ? o1.getKey().compareTo(o2.getKey())
                : o1.getValue().compareTo(o2.getValue()) : o2.getValue().compareTo(o1.getValue()) == 0
                ? o2.getKey().compareTo(o1.getKey())
                : o2.getValue().compareTo(o1.getValue()));
        return list.stream().collect(Collectors.toMap(Entry::getKey, Entry::getValue, (a, b) -> b, LinkedHashMap::new));
    }


	private List<List<String>> criticalConnections(Graph G) 
	{
		HashMap<String, Integer> disc = new HashMap<String, Integer>();
		HashMap<String, Integer> low = new HashMap<String, Integer>();
		
		List<List<String>> res = new ArrayList<>();

		for(String v : G.adjList.keySet())
		{
			disc.put(v, -1);
		}
	
		for(String i : G.adjList.keySet())
		{
			if (disc.get(i) == -1) 
			{
				dfs(G, i, low, disc, res, i);
			}
		}
		return res;
	}
	
	int time = 0;
	
	private void dfs(Graph G, String u, HashMap<String, Integer> low, HashMap<String, Integer> disc, List<List<String>> res, String pre) 
	{
		time++;
		disc.put(u, time);
		low.put(u, time);

		for(String v : G.adjList.get(u))
		{
			if (v.equals(pre)) 
			{
				continue;
			}
			if (disc.get(v) == -1) 
			{
				dfs(G, v, low, disc, res, u);
				low.put(u, Math.min(low.get(u), low.get(v)));
				
				if (low.get(v) > disc.get(u)) 
				{
					res.add(Arrays.asList(u, v));
				}
			} 
			else 
			{
				low.put(u, Math.min(low.get(u), disc.get(v)));
			}
		}
	}


	private HashMap<String, Float> detectFraudPR(Graph G)
	{
		System.out.println("Calculating PageRank...");
		
		HashMap<String, Float> results = calcPageRank(G, 0.5, 0);

		System.out.println("Done");
		return results;
	}


	HashMap<String, Float> calcPageRank(Graph G, double eps, int iterations)
	{
		HashMap<String, Float> pr = new HashMap<String, Float>();
		for(var entry : G.adjList.entrySet()) 
		{
			String s = entry.getKey();
			pr.put(s, (float)1.0 / G.n);
		}
		
		Float diff = Float.MAX_VALUE;
		HashMap<String, Float> nextIter;

		while(iterations < 2)
		{
			nextIter = singleIterationCalcPageRank(G, pr);
			diff = diff(G, nextIter, pr);
			pr = nextIter;
			iterations++;
		}

		return(pr);
	}


	HashMap<String, Float> singleIterationCalcPageRank(Graph G, HashMap<String, Float> pr) 
	{
		float BETA = (float)1;

		HashMap<String, Float> nextIter = new HashMap<String, Float>();
		for(var entry : G.adjList.entrySet()) 
		{
			String s = entry.getKey();
			nextIter.put(s, (1 - BETA) / G.n);
		}
		
		float P;
		ArrayList<String> out;

		for(var entry : G.adjList.entrySet()) 
		{
			String s = entry.getKey();

			if(G.adjList.get(s).size() == 0) 
			{
				for(var set : G.adjList.entrySet()) 
				{
					String t = set.getKey();
					P = nextIter.get(t) + BETA * pr.get(s) / G.n;
					nextIter.put(t, P);
				}
			} 
			else 
			{
				out = G.adjList.get(s);
				for(String t : out) 
				{
					P = nextIter.get(t) + BETA * pr.get(s) / out.size();
					nextIter.put(t, P);
				}
			}
		}

		return(nextIter);
	}


	float diff(Graph G, HashMap<String, Float> nextIter, HashMap<String, Float> prevIter) 
	{
		float sum = 0;

		for(var entry : G.adjList.entrySet()) 
		{
			String s = entry.getKey();
			sum += Math.abs(nextIter.get(s) - prevIter.get(s));
		}

		return (float) Math.sqrt(sum);
	}


	private Graph createGraph(String csv)
	{
		String infoId;
		JSONObject csvObj = new JSONObject(csv);
		JSONArray edgelistArr = csvObj.getJSONArray("edgelist");
		JSONArray infoArr;

		String edgeListId = csvObj.getString("edgeListId");

		Graph G = new Graph();

		System.out.println("Parsing edgelist....");

		for (int i = 0; i < edgelistArr.length(); i++) 
		{
			JSONObject obj = edgelistArr.getJSONObject(i);
			try 
			{
				String id1 = obj.get(edgeListId + "_1").toString();
				String id2 = obj.get(edgeListId + "_2").toString();

				G.addEdge(id1, id2);
				G.m++;
			} 
			catch (Exception e) {}
		}

		System.out.println("Done");
		
		try 
		{
			infoArr = csvObj.getJSONArray("info");

			System.out.println("Parsing vertex information...");

			infoId = csvObj.getString("id");
			JSONArray headersArr = csvObj.getJSONArray("headers");

			for(int i = 0; i < infoArr.length(); i++) 
			{
				JSONObject obj = infoArr.getJSONObject(i);

				HashMap<String, String> temp = new HashMap<String, String>();
				
				for(int j = 0; j < headersArr.length(); j++)
				{
					String header = headersArr.getString(j);
					temp.put(header, obj.getString(header));
				}

				G.addInfo(obj.getString(infoId), temp);
			}

			System.out.println("Done");
		} 
		catch (Exception e) {}

		G.n = G.adjList.size();
		return G;
	}
}


class Graph
{
	HashMap<String, ArrayList<String>> adjList;
	HashMap<String, ArrayList<String>> parents;
	HashMap<String, HashMap<String, String>> infoList;

	int n;
	int m;

	public Graph()
	{
		this.adjList = new HashMap<String, ArrayList<String>>();
		this.infoList = new HashMap<String, HashMap<String, String>>();
		this.parents = new HashMap<String, ArrayList<String>>();

		this.n = 0;
		this.m = 0;
	}


	public void addVertex(String v)
	{
		if(!adjList.keySet().contains(v))
		{
			adjList.put(v, new ArrayList<String>());
		}

		if(!parents.keySet().contains(v))
		{
			parents.put(v, new ArrayList<String>());
		}
	}


	public void addEdge(String from, String to)
	{
		addVertex(from);
		addVertex(to);

		try 
		{
			ArrayList<String> temp = adjList.get(from);
			ArrayList<String> parent = parents.get(to);

			temp.add(to);
			parent.add(from);

			adjList.put(from, temp);
			parents.put(to, parent);
		} 
		catch (Exception e) 
		{
			
			ArrayList<String> temp = new ArrayList<String>();
			ArrayList<String> parent = new ArrayList<String>();

			temp.add(to);
			parent.add(from);

			adjList.put(from, temp);
			parents.put(to, parent);
		}
	}


	public boolean containsEdge(String from, String to)
	{
		return this.adjList.get(from).contains(to);
	}


	public void addInfo(String v, HashMap<String, String> info)
	{
		infoList.put(v, info);
	}


	public ArrayList<String> getNeighbors(String v)
	{
		return adjList.get(v);
	}


	public void printNeighbors()
	{
		for(var entry : this.adjList.entrySet())
		{
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}
	}


	public void printInfo(String v)
	{
		for(var entry : infoList.get(v).entrySet()) 
		{
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}
	}


	public void printEdges()
	{
		for(var entry : adjList.entrySet()) 
		{
			System.out.println(entry.getKey() + " to " + entry.getValue());
		}
	}
}