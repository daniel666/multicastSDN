package net.floodlightcontroller.topology;

import java.util.ArrayList;

import net.floodlightcontroller.routing.Link;

class MetricEdge implements Comparable<MetricEdge>{
	Long src;
	Long dest;
	ArrayList<Link> actualPath;
	int metricCost;
				
	public MetricEdge(){
		actualPath = new ArrayList<Link>();
		
	}
	public MetricEdge(Long src, Long dest, ArrayList<Link> acttualPath,
			int metricCost) {
		super();
		this.src = src;
		this.dest = dest;
		this.actualPath = acttualPath;
		this.metricCost = metricCost;
	}
	
	public MetricEdge(MetricEdge edge) {
		// TODO Auto-generated constructor stub
		if(edge == null)
			return;
		this.src = edge.src;
		this.dest = edge.dest;
		this.actualPath = new ArrayList<Link>();
		this.actualPath.addAll(edge.actualPath);
		this.metricCost = edge.metricCost;
	}

	@Override	
	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append("(" + src);
		if(actualPath!=null)
			for(Link link: actualPath){
				sb.append("-->" + link.getDst());
			}
		else
			sb.append("-->" + dest);
		sb.append("|" + metricCost +")");
		return sb.toString();
	}
	
	@Override
    public boolean equals(Object obj){
		if (!(obj instanceof MetricEdge))
	            return false;
		if (obj == this)
	            return true;
		MetricEdge other = (MetricEdge) obj;
		if(src.equals(other.src) == false|| dest.equals(other.dest) == false){
				return false;
		}
		return true;
	}
	@Override
	public int compareTo(MetricEdge other) {
		// TODO Auto-generated method stub
		//sort in descending order of cost, and then metricedge's src switch id
		if (metricCost != other.metricCost)
			return -metricCost + other.metricCost;
		return (int) (-src + other.src);
	}
	 @Override
	 public int hashCode() {
	        final int prime = 31;
	        int result = 1;
	        result = prime * result + (int) (dest ^ (dest >>> 32));
	        result = prime * result + (int) (src ^ (src >>> 32));
	        return result;
	    }
	
	Long getOtherEnd(Long v){
		return v == src? dest: src;
	}
	
	boolean isSrc(Long v){
		return v == src;
	}
	
	MetricEdge reverse(){
		MetricEdge ret = new MetricEdge();
		ret.src = this.dest;
		ret.dest = this.src;
		ret.metricCost = this.metricCost;
		if(this.actualPath == null)
			ret.actualPath = null;
		else{
			for(int i = this.actualPath.size()-1; i>=0; i--){
				Link link = this.actualPath.get(i);
				Link revlink = link.getSymLink();
				ret.actualPath.add(revlink);
			}
		}
		return ret;
		
	}
}