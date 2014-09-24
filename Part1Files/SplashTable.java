import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Hashtable;
import java.util.Random;

/**
 * Represents a splash table as described
 * in http://bit.ly/1lvEmyJ
 * 
 * With specific naming conventions as follows
 * B: bucket size, power of 2
 * R: re-insertions before declaring 
 * 	  an insertion to be failed
 * S: log_2(# entries in the table); 
 * 	  2^S = #entries
 * h: number of hash functions; h 
 * 	  random odd 32-bit multipliers
 * entry: consists of a 32-bit integer key 
 * 		  and a 32-bit integer payload
 * 
 * Place key in the least-loaded bucket with ties broken
 * arbitrarily
 * 
 */

public class SplashTable {
	int B;
	int R;
	int S;
	int h;
	int N;
	int numBuckets;
	Entry[][] table;
	long[] multipliers;
	
	/**
	 * 
	 * @param B: bucket size, power of 2
	 * @param R: re-insertions before declaring 
	 * 	  		 an insertion to be failed
	 * @param S: log_2(# entries in the table); 
	 * 	  		 2^S = #entries
	 * @param h: number of hash functions; h 
	 * 	  		 random odd 32-bit multipliers
	 */
	public SplashTable(int B, int R, int S, int h){
		this.B = B;
		this.R = R;
		this.S = S;
		this.h = h;		
		multipliers = new long[h];
		numBuckets = ((int)Math.pow(2, this.S))/this.B;		
		table = new Entry[numBuckets][this.B];
		multipliers = setMultipliers(this.h);
		initializeTable();
	}	

	//return 0 if not present
	/**
	 * Search for key's value in table
	 * @param key to be looked up
	 * @return associated pay-load; return 0 if no corresponding payload
	 * 		   Note that 0 is not a possible value
	 */
	public long probe(long key){
		int[] allBuckets = getAllBucketsForKey(key);
		long payload = 0;
		for(int i = 0; i < h; i++){
			for(int j = 0; j < B; j++){
				payload += (table[allBuckets[i]][j].getKey() == key && payload==0) ? table[allBuckets[i]][j].getPayload() : 0;
			}
		}
		return payload;
	}
	
	/**
	 * insert single pair wrapper function
	 * @param key
	 * @param payload
	 * @return true if insertion did not cause failure;
	 * 		   false if it did cause failure
	 */
	public boolean build(long key, long payload){
		return build(key, payload, 0);
	}

	/**
	 * insert single pair, called recursively
	 * @param key
	 * @param payload
	 * @param count current count of how many re-insertions 
	 * 		  have been done
	 * @return true if insertion did not cause failure;
	 * 		   false if it did cause failure
	 */
	private boolean build(long key, long payload, int count){		
		if(count > R){
			return false;
		}
		
		int leastFullBucket = getLeastFullBucket(key);
		if(leastFullBucket == -1){
			return true;//stop method without inserting value
		}
		
		int fillCount = getFillCount(leastFullBucket);
		
		if(fillCount < B){
			table[leastFullBucket][fillCount].setKey(key);
			table[leastFullBucket][fillCount].setPayload(payload);
			N++;
		} else {
			//remove oldest
			Entry oldest = table[leastFullBucket][0];
			//shift left by one
			for(int i = 0; i < B-1; i++){
				table[leastFullBucket][i] = table[leastFullBucket][i+1];
			}
			//insert new entry at end
			table[leastFullBucket][B-1] = new Entry(key, payload);
			//reinsert old entry
			return build(oldest.getKey(), oldest.getPayload(), ++count);
		}
		return true;
	}
	
	/**
	 * Find bucket index with lowest capacity for all matching
	 * buckets for this key
	 * @param key to lookup
	 * @return -1 if key already in table; bucket with lowest
	 * capacity otherwise
	 */
	public int getLeastFullBucket(long key){
		int[] allBuckets = getAllBucketsForKey(key);
		
		//check if key already present
		//"If you detect a key insertion that is already 
		//in the table, you can ignore the insertion."- K.R.
		for(int bucket: allBuckets){
			for(int i = 0; i < this.B; i++){
				if(table[bucket][i].getKey()==key){
					return -1;
				}
			}
		}

		
		//shuffle buckets to randomize which bucket 
		//to put into in case all full note after this 
		//step the emptiest bucket chosen, which can be full
		Random generator = new Random();
		for(int j = 0; j < allBuckets.length * 5; j++){
			int x = generator.nextInt(allBuckets.length); int y = generator.nextInt(allBuckets.length);	
			int temp = allBuckets[x];
			allBuckets[x] = allBuckets[y];
			allBuckets[y] = temp;
		}
		
		int leastFullBucket = allBuckets[0];
		int lowestFullness = this.B;
		for(int buck: allBuckets){
			int fillCount = getFillCount(buck);
			if(fillCount < lowestFullness){
				leastFullBucket = buck;
				lowestFullness = fillCount;
			}
		}
		return leastFullBucket;
	}
	
	/**
	 * Get fill count of bucket
	 * @param bucket
	 * @return number of entries contained
	 */
	public int getFillCount(int bucket){
		int count = 0;
		for(int i = 0; i < this.B; i++){
			if(table[bucket][i].getKey()!=0){
				count++;
			}
		}
		return count;
	}
	
	/**
	 * Find buckets key is matched to
	 * @param key
	 */
	public int[] getAllBucketsForKey(long key){
		int[] buckets = new int[this.multipliers.length];
		for(int i = 0; i < h; i++){
			buckets[i] = getBucket(key, multipliers[i]);
		}
		return buckets;
	}
	
	
	/**
	 * This follows from reference doc section 2 intro
	 * and Professor on https://piazza.com/class/hqep8bmhdrv5xb?cid=19
	 * Find bucket for key, multiplier pair
	 * 1. First multiply key and multiplier and store
	 * 	  lower 32 bits in R
	 * 2. Based on:
	 * 	  a. logb(m/n) = logb(m) - logb(n)
	 *    b. log2(2^S/B) = log2(2^S) - log2(B)
	 *    we set shift to S - log2(2^S/B)
	 * 3. We then rght-shift R by 32-shift 
	 * @param key
	 * @param multiplier
	 * @return bucket
	 */
	private int getBucket(long key, long multiplier){
		long R = (key * multiplier) & ((1L << 32) - 1);
		int shift = ( S - (int)(Math.log(this.B)/Math.log(2)) );	
		return (int) ((shift == 0) ? 0 : R >> (32 - shift));
	}
	
	private void initializeTable(){
		for(int i = 0; i < table.length; i++){
			for(int j = 0; j < table[0].length; j++){
				table[i][j] = new Entry(0, 0);;
			}
		}
	}
	
	/**
	 * generate odd, positive multipliers less than 2^32
	 * @param h number of multipliers requested
	 * @return multipliers
	 */
	public static long[] setMultipliers(int h){
		long[] multpliers = new long[h];
		Random r = new Random();
		for(int i = 0; i < multpliers.length; i++){
			long possOdd = r.nextLong();
			while(possOdd % 2 == 0 || possOdd < 0){
				possOdd = r.nextLong();
			}
			multpliers[i] = possOdd % (1L << 32);
		}
		return multpliers;
	}
	
	/**
	 * 
	 * @return load factor: N/(2^S)
	 */
	public double getLoadFactor(){
		return (1.0 *this.N)/(1 << this.S);
	}
		
	/**
	 * Dumps to file
	 * @param fileName
	 * @throws FileNotFoundException
	 * @throws UnsupportedEncodingException
	 */
	public void dump(String fileName) throws FileNotFoundException, UnsupportedEncodingException{
		String dumpData = dump();
		PrintWriter writer = new PrintWriter(fileName);		
		writer.print(dumpData);
		writer.close();
	}
	
	/**
	 * Generates dumpfile string
	 * @return dumpfile string
	 */
	public String dump(){
		StringBuilder sb = new StringBuilder();
		sb.append(B+" "+S+" "+h+" "+N+"\n");
		for(long mulitplier: multipliers){
			sb.append(mulitplier + " ");
		}
		sb.append('\n');
		for(int i = 0; i < table.length; i++){
			for(int j = 0; j < B; j++){
				sb.append(table[i][j].getKey() + " " + table[i][j].getPayload());
				sb.append('\n');
			}
		}
		return sb.toString();
	}
	
}