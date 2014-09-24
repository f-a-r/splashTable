/**
 * Entry tuple in splash table
 * Pair of key and pay-load with
 * getters and setters.
 *
 */
public class Entry {
	private long key;
	private long payload;
	
	public Entry(long key, long payload){
		this.setKey(key);
		this.setPayload(payload);
	}	

	public long getPayload() {
		return payload;
	}

	public void setPayload(long payload) {
		this.payload = payload;
	}

	public long getKey() {
		return key;
	}

	public void setKey(long key2) {
		this.key = key2;
	}
	
	
}
