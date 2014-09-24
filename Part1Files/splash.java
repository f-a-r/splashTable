import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Scanner;
/**
 * Takes care of input/output of splash table experimentation
 */
public class splash {
	
	public static void main(String[] args) throws Exception{		
		//Read in command line arguments; basic error checking
		int B = Integer.parseInt(args[0]);
		int R = Integer.parseInt(args[1]);
		int S = Integer.parseInt(args[2]);
		int h = Integer.parseInt(args[3]);
		String inputfile = args[4];
		String dumpfile = "dumpfile-default";
		if(args.length > 5){
			dumpfile = args[5];
		}
		if(Math.pow(2, S) < B){
			System.out.println("2^S < B");
			return;
		}	
		
		//Create table and build form inputfile
		SplashTable st = new SplashTable(B, R, S, h);
		boolean dump = false;
		BufferedReader br = new BufferedReader(new FileReader(inputfile));
		try {
	        String line = br.readLine();
	        while (line != null && !dump) {
		        String[] pair = line.split(" ");
		        if(!st.build(Long.parseLong(pair[0]), Long.parseLong(pair[1]))){
		        		dump = true;
		        }
	            line = br.readLine();	            
	        }
		} catch(Exception e) {
        		System.out.println(e);
        } finally {
	        br.close();
	    }
		
		//Dump, read from stdin, write to stdout
	    if(dump){
	    		System.out.println("R reinsertions exceeded. Dumping to file...");	    		
	    		st.dump(dumpfile);
	    } else {
	    		//read probefile from stdin
	    		Scanner sc = new Scanner(System.in);
	    		while(sc.hasNextLine()){	    			
	    			long key = Long.parseLong(sc.nextLine());
		    		//write resultfile to stdout
	    			long payload = st.probe(key);
	    			if(payload != 0){
	    				System.out.println(key + " " + payload);
	    			}
	    		}	    	
	    		st.dump(dumpfile);
	    }	    
	}
}