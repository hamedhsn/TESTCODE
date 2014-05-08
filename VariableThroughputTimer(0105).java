// TODO: fight with lagging on start
// TODO: create a thread which will wake up at least one sampler to provide rps
package kg.apc.jmeter.timers;

import java.util.ArrayList;
import java.util.List;

import kg.apc.jmeter.JMeterPluginsUtils;

import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.engine.event.LoopIterationEvent;
import org.apache.jmeter.engine.util.NoThreadClone;
import org.apache.jmeter.gui.util.PowerTableModel;
import org.apache.jmeter.testelement.AbstractTestElement;
import org.apache.jmeter.testelement.TestListener;
import org.apache.jmeter.testelement.property.CollectionProperty;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.NullProperty;
import org.apache.jmeter.testelement.property.PropertyIterator;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.timers.ConstantThroughputTimer;
import org.apache.jmeter.timers.Timer;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;

//hsn
import org.apache.jmeter.samplers.SampleResult;
import java.util.*;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.LineNumberReader;
import java.io.*;
//import java.util.logging.Logger;


/**
 *
 * @author undera
 * @see ConstantThroughputTimer
 */
public class VariableThroughputTimer
        extends AbstractTestElement
        implements Timer, NoThreadClone, TestListener {

    public static final String[] columnIdentifiers = new String[]{
        "Start RPS", "End RPS", "Duration, sec"
    };
    public static final Class[] columnClasses = new Class[]{
        String.class, String.class, String.class
    };
    // TODO: eliminate magic property
    public static final String DATA_PROPERTY = "load_profile";
    public static final int DURATION_FIELD_NO = 2;
    public static final int FROM_FIELD_NO = 0;
    public static final int TO_FIELD_NO = 1;
    private static final Logger log = LoggingManager.getLoggerForClass();
    //private static final Logger loghsn1 = LoggingManager.getLoggerForClass();
    private static final java.util.logging.Logger loghsn = java.util.logging.Logger.getLogger("my.logger");
    /* put this in fields because we don't want create variables in tight loops */
    private long cntDelayed;
    private double time = 0;
    private double msecPerReq;
    private long cntSent;
    private double rps;
    private double startSec = 0;
    private CollectionProperty overrideProp;
    private int stopTries;
    private double lastStopTry;
    private boolean stopping;
    //hsn
    //public static SampleResult reslt;
    private CollectionProperty rows = new CollectionProperty(DATA_PROPERTY, new ArrayList<Object>());
    public static int hitCnt=0;
    public boolean isIncrease=false;
    public int exprps=640;
    public int baserps=0;
    public int prevrpstobebase=0;
    public static Map<Long, List<Integer>> multiMapStartTimeLatency = new HashMap<Long, List<Integer>>(); //map of <startTime,Latency>
    public static Map<Long, List<Integer>> multiMapEndTimeLatency   = new HashMap<Long, List<Integer>>(); //map of <EndTime,Latency>
    public static ArrayList<Long> listOfStartTime=new ArrayList();//to be deleted     	
    public static ArrayList<Integer> realOpsLastDuration=new ArrayList();
    public static ArrayList<Integer> wind50latency=new ArrayList();
    public static ArrayList<Integer> wind75latency=new ArrayList();
    public static ArrayList<Integer> wind90latency=new ArrayList();
    public static ArrayList<Integer> percentileLatPerSec=new ArrayList();
    public static Map<Integer, List<Integer>> percentilewindow = new HashMap<Integer, List<Integer>>();    
    public int tmpMaxActualOps=0;
    public int MaxActualOps=9999999;
    public int MaxAcceptedOps=0;
    public int prevAcceptedOps=0;//just for the endpoint
    public int repeatCnt=0;//number of times we repeat the same AcceptedOps
    private double xSecMonitor=1000.0; //monitoring x msec before the newest instance 
    public Map<Integer, Integer> mapTimeOpsec = new TreeMap<Integer, Integer>();	//Map of <time,operationPerSecond>
    int lat50decider=0;
    int lat75decider=0;
    int lat90decider=0;
    public int timeEnd=10;
    public int timeStart=0;
    public int duration=10;
    public int numSecPassedCond=0;
    public boolean numFailRepeate=false;
    public double avgPingLatency;
    public String host="";
    public int incGap=2;
    
    public VariableThroughputTimer() {
        super();
        trySettingLoadFromProperty();
        //hsn
        //createRows(exprps,2);//hsn
        createRows(exprps,duration+2);//hsn
        
        avgPingLatency=Double.parseDouble(readAvgLatencyFromFile("tmp1.txt")); //reading the avgping latency from tmp1.txt file
        host=readAvgLatencyFromFile("tmp2.txt");
        
//        loghsn.setLevel(java.util.logging.Level.INFO);
//        loghsn.setUseParentHandlers(false);
//        java.util.logging.ConsoleHandler handler = new java.util.logging.ConsoleHandler();
        //handler.setLevel(java.util.logging.Level.ALL);
//        handler.setFormatter(new java.util.logging.SimpleFormatter());
//       loghsn.addHandler(handler);
        
//        try{java.util.logging.FileHandler ff=new java.util.logging.FileHandler("loghsn");
//        loghsn.addHandler(ff);
        //ff.pattern = %h/myApp.log;
//        java.util.logging.SimpleFormatter formatter = new java.util.logging.SimpleFormatter();
//        ff.setFormatter(formatter);
        //logger.setLevel(Level.ALL);
//        }catch(java.io.IOException e){}
//        loghsn.info("getting into constructor");
        //loghsn.log("log log");
        
        
    }

    //hsn
    private String readAvgLatencyFromFile(String fn){
        String lineData = "";
        try{
        RandomAccessFile inFile = new RandomAccessFile(fn,"r");
        lineData = inFile.readLine();
        System.out.println("The line is: "+ lineData);
        inFile.close();
        }//try
        catch(IOException ex){
        System.err.println(ex.getMessage());
        }//catch
        return lineData;
        
    	/*
    	File file = new File(fn);
        if (file.exists()){
        FileReader fr = new FileReader(file);
        LineNumberReader ln = new LineNumberReader(fr);
        while (ln.getLineNumber() == 0){
        String s = ln.readLine();
        System.out.println("The line is: "+ s);
        return s;
        }
        }
        */
        }
    
    public static void addreslt(SampleResult reslt){
	//Adding the Start time and latency
    	long stTimeBase=reslt.getStartTime()-(reslt.getStartTime()%1000);
    	//System.out.println("done in:"+(reslt.getStartTime()-(reslt.getStartTime()%1000)));
    	//listOfStartTime.add(stTimeBase);
    	//System.out.println("done out");
    	
    	List<Integer> vls = multiMapStartTimeLatency.get(stTimeBase);
    	if (vls == null) {
    		vls=new  ArrayList<Integer>();
    		multiMapStartTimeLatency.put(stTimeBase, addDicValue(vls,(int)reslt.getLatency()));
    	}
    	else{
    		multiMapStartTimeLatency.put(stTimeBase, addDicValue(vls,(int)reslt.getLatency()));
    	}
    	//System.out.println("key: "+stTimeBase+" Values: "+multiMapStartTimeLatency.get(stTimeBase));    		

    	if (multiMapStartTimeLatency.get(stTimeBase-20000)!=null){
    		//System.out.println("REMOVED "+(stTimeBase-20000));
    		multiMapStartTimeLatency.remove(stTimeBase-20000);
    	}
    	
	//Adding the End time and latency
    	long endTimeBase=reslt.getEndTime()-(reslt.getEndTime()%1000);
    	vls = multiMapEndTimeLatency.get(endTimeBase);
    	if (vls == null) {
    		vls=new  ArrayList<Integer>();
    		multiMapEndTimeLatency.put(endTimeBase, addDicValue(vls,(int)reslt.getLatency()));
    	}
    	else{
    		multiMapEndTimeLatency.put(endTimeBase, addDicValue(vls,(int)reslt.getLatency()));
    	}

    	if (multiMapEndTimeLatency.get(endTimeBase-20000)!=null){
    		//System.out.println("REMOVED "+(stTimeBase-20000));
    		multiMapEndTimeLatency.remove(endTimeBase-20000);
    	}

/*to be deleted    	if (multiMapStartTimeLatency.containsKey(stTimeBase)){
    		//System.out.println("");
    		multiMapStartTimeLatency.put(stTimeBase, addDicValue(multiMapStartTimeLatency.get(stTimeBase),reslt.getLatency()));
    	}
    	else{
    		List<Long> vls=new  ArrayList<Long>();
    		multiMapStartTimeLatency.put(stTimeBase, addDicValue(vls,reslt.getLatency()));
    	}
*/    	
    }
    
    public static List<Integer> addDicValue(List<Integer> values,int latency){
    	values.add(latency);
    	return values;
    }
    
    //end of hsn
    
    public long delay() {
        synchronized (this) {

            while (true) {
                int delay;
                long curTime = System.currentTimeMillis();
                long msecs = curTime % 1000;
                long secs = curTime - msecs;
                checkNextSecond(secs);
                //System.out.println("hsn test"+System.currentTimeMillis());
                //hsn
//                if (((long)startSec+incGap<secs)){
//                //if (hitCnt>80){
//                	incGap+=10000;
//                	exprps=exprps*2;
//                	//hitCnt=0;
//                    System.out.println("//***after 10 secs: "+(long)startSec+" now: "+secs);
//                	createRows(exprps,10);
//                }
//                else{
//                	isIncrease=false;
//                }//end of hsn
                delay = getDelay(msecs);

                if (stopping) {
                    delay = delay > 0 ? 10 : 0;
                    notify();
                }

                if (delay < 1) {
                    notify();
                    break;
                }
                cntDelayed++;
                try {
                    wait(delay);
                } catch (InterruptedException ex) {
                    log.error("Waiting thread was interrupted", ex);
                }
                cntDelayed--;
            }
            cntSent++;
        }
        return 0;
    }

    //hsn print the number of operation started in previous second
    //public void printPrevSec{
    	
    //}

    //Calculate p percentile 
   	public double percentile(List<Integer> values,double p) {
 
    	if ((p > 100) || (p <= 0)) {
    		throw new IllegalArgumentException("invalid quantile value: " + p);
    	}
    	double n = (double) values.size();
    	if (n == 0) {
    		return 0;
    	}
    	if (n == 1) {
    		return values.get(0); // always return single value for n = 1
    	}
    	double pos = p * (n) / 100;
    	double fpos = Math.floor(pos);
    	int intPos = (int) fpos;
    	double dif = pos - fpos;
    	//Collections.sort(values); //already calculated
        if (pos < 1) {
        	return values.get(0);
        }
        if (pos >= n) {
        	return values.get(values.size() - 1);
        }
        double lower = values.get(intPos - 1);
        double upper = values.get(intPos);
    	return (lower + dif * (upper - lower));
   	}
   	
   	public void rpsBaseDecision(int realOpsecCnt,int expCnt) {
        //System.out.println("Number of operation per second at "+(long)(secs-xSecMonitor)+" which is "+xSecMonitor+" millisecond before: "+expCnt);
        if (realOpsecCnt<(expCnt-(expCnt*0.2))){ //if hit number goes below 80% of what we expected
            System.out.println("---------------------------------------------------------------");
            //System.out.println("Time:"+secFromZero);
            //System.out.println("!!WARNING(Below 10%): Expected op/sec: "+expCnt+" Actual op/sec: "+realOpsecCnt+"  ...........");
            //System.out.println("---------------------------------------------------------------");        
        }
        else{
        	if (expCnt!=5){//0 op/per/sec
        		numSecPassedCond++;
        	}
        	
        }
   	}
   	
   	public void hitBaseDecision(int realHitCnt,int expCnt) {
        //System.out.println("Number of operation per second at "+(long)(secs-xSecMonitor)+" which is "+xSecMonitor+" millisecond before: "+expCnt);
        if (realHitCnt<(expCnt-(expCnt*0.2))){ //if hit number goes below 80% of what expected to be
            System.out.println("---------------------------------------------------------------");
            //System.out.println("Time:"+secFromZero);
            //System.out.println("!!WARNING(Below 10%): Expected op/sec: "+expCnt+" Actual op/sec: "+realOpsecCnt+"  ...........");
            //System.out.println("---------------------------------------------------------------");        
        }
        else{
        	if (expCnt!=5){//0 op/per/sec
        		numSecPassedCond++;
        	}
        	
        }
   	}

   	public void latencyBaseDecision(double firstTF,double secondF,double thirdSF,double forthN) {
   		if (incGap==2){
   	   		if (secondF<(avgPingLatency+5)){
   				System.out.println("FIRST PART:");
        		numSecPassedCond++;
   	   		}
   		}
   		else{
   			if ((thirdSF<lat75decider)&&(forthN<lat90decider)){
   				System.out.println("SECOND PART:");
        		numSecPassedCond++;
   			}
   		}
   	}

   	public void latencyAndRpsBaseDecision(int realOpsecCnt,int expCnt,double firstTF,double secondF,double thirdSF,double forthN) {
   		//if ((forthN<(avgPingLatency+30))&&(realOpsecCnt>(expCnt-(expCnt*0.10)))){
   		int threshold=20;
   		if ((secondF<(avgPingLatency+threshold))){
   			if (expCnt!=5){
        		numSecPassedCond++;
        		System.out.println("Number of secs passed the criteria: "+numSecPassedCond);
   			}
   		}
   	}

   	public void doCommand(List<String> command)   
   		  throws IOException  
   		  {  
   		   String s = null;  
   		   ProcessBuilder pb = new ProcessBuilder(command);  
   		   Process process = pb.start();  
   		   BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));  
   		   BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));  
   		   // read the output from the command  
   		   //System.out.println("Here is the standard output of the command:\n");  
   		   while ((s = stdInput.readLine()) != null)  
   		   {  
   		    System.out.println(s);  
   		   }  
   		   // read any errors from the attempted command  
   		   //System.out.println("Here is the standard error of the command (if any):\n");  
   		   while ((s = stdError.readLine()) != null)  
   		   {  
   		    System.out.println(s);  
   		   }  
   		  } 
   	
   	public void anticipatRpsBaseActualRps1(double secs) {
   	   	
        //System.out.println("previous sec:"+(long)(secs-1000.0));
        
        //System.out.println("Number of requests sent at "+(long)(secs-xSecMonitor)+" which is "+xSecMonitor+" millisecond before: "+freq);
        int realHitCnt=multiMapStartTimeLatency.get((long)(secs-xSecMonitor)).size();//Actual Hit Count

        //System.out.println("Number of requests sent at "+(long)(secs-xSecMonitor)+" which is "+xSecMonitor+" millisecond before: "+realHitCnt);
        int realOpsecCnt=multiMapEndTimeLatency.get((long)(secs-xSecMonitor)).size();//Actual op/per/sec
        
        //calculateing Percentile
        //System.out.println("key: "+(long)(secs-xSecMonitor)+" Values: "+multiMapStartTimeLatency.get((long)(secs-xSecMonitor)));
        List<Integer> values=multiMapEndTimeLatency.get((long)(secs-xSecMonitor));
        Collections.sort(values);
        
        int secFromZero=((int)((secs-xSecMonitor)-startSec)/1000);//converting the secs to value start from 0 not startSec
        int expCnt=mapTimeOpsec.get(secFromZero);
        
        /* ********** Add infinity(100000) for the Loss value ***************
        if (expCnt>realOpsecCnt){
            for (int i=0;i<(expCnt-realOpsecCnt);i++){
            	values.add(100000);
            }
        }*/

        double firstQrtl=percentile(values,25);
        double secQrtl=percentile(values,50);
        double thirdQrtl=percentile(values,75);
        double maxQrtl=percentile(values,90);
        wind50latency.add((int)secQrtl);
        wind75latency.add((int)thirdQrtl);
        wind90latency.add((int)maxQrtl);
        percentileLatPerSec.clear();
        for (int i=1;i<100;i++){
        	List<Integer> vls = percentilewindow.get(i);
        	if (vls == null) {
        		vls=new  ArrayList<Integer>();
        	}
        	int per=(int)percentile(values,i);
        	if (i%2==0){
        		percentileLatPerSec.add(per);
        	}
    		percentilewindow.put(i, addDicValue(vls,per));
        }

        //System.out.println("Latency Values:"+windlatency);
        
        //rpsBaseDecision(realOpsecCnt,expCnt);						//adding to the table based on OPS         
        //hitBaseDecision(realHitCnt,expCnt);        				//adding to the table based on HitPerSecond
        //latencyBaseDecision(firstQrtl,secQrtl,thirdQrtl,maxQrtl);   //adding to the table based on Latency
        realOpsLastDuration.add(realOpsecCnt);
        //latencyAndRpsBaseDecision(realOpsecCnt,expCnt,firstQrtl,secQrtl,thirdQrtl,maxQrtl);   //adding to the table based on Latency&Rps
        latencyBaseDecision(firstQrtl,secQrtl,thirdQrtl,maxQrtl);
        
        //System.out.println("secFromZero:"+secFromZero+"  timeStart:"+timeStart+" timeEnd:"+timeEnd);
        if (secFromZero==timeEnd){
        	/* *********** PING ************/
        	List<String> commands = new ArrayList<String>();  
        	commands.add("sudo");
        	commands.add("tcpping");
        	commands.add("-x");  
        	commands.add("1");  
        	commands.add(host);  
        	try{
        	doCommand(commands);
        	}
        	catch (IOException ex){
        		
        	}
        	//System.out.println("secFromZero==timeEnd"+secFromZero+" = "+timeEnd);
        	System.out.println("numSecPassedCondexpected:"+(int)((timeEnd-timeStart-(xSecMonitor/1000))*0.5)+" <=numSecPassedCond: "+numSecPassedCond);
        	if ((int)((timeEnd-timeStart-(xSecMonitor/1000))*0.5)<=numSecPassedCond){
        		wind50latency.clear();
        		wind75latency.clear();
        		wind90latency.clear();
        		percentilewindow.clear();
    			if (incGap==2){
    				exprps=exprps*2;
    			}else{
    				System.out.println("************************Increament: "+incGap);
    				exprps=exprps+incGap;
    			}
        			if (MaxAcceptedOps<expCnt){

        				MaxAcceptedOps=expCnt;
        			}
        			createRows(exprps,duration);
        	}
        	else{//first time failed
        		if ((numFailRepeate==false)&& (incGap==2)){//first time failed-reapeat again with the same rps
            		numFailRepeate=true;
            		createRows(exprps,duration);
            		//createRows(5,duration);
        		}
        		else{																//two times failed
        			if (incGap==2){
        				Collections.sort(wind50latency);
        				Collections.sort(wind75latency);
        				Collections.sort(wind90latency);
        				lat50decider=(int)percentile(wind50latency,25);
        				lat75decider=(int)percentile(wind75latency,25);
        				lat90decider=(int)percentile(wind90latency,25);
        				lat50decider-=lat50decider*1;
        				lat75decider-=lat75decider*1;
        				lat90decider-=lat90decider*1;
        				System.out.println("Latency Values:"+wind50latency);
        				System.out.println("5025thvalues:"+(int)percentile(wind50latency,25));
        				System.out.println("5050thvalues:"+(int)percentile(wind50latency,50));
        				System.out.println("7525thvalues:"+(int)percentile(wind75latency,25));
        				System.out.println("7550thvalues:"+(int)percentile(wind75latency,50));
        				System.out.println("9025thvalues:"+(int)percentile(wind90latency,25));
        				System.out.println("9050thvalues:"+(int)percentile(wind90latency,50));

        				for (int i=1;i<100;i++){
        					System.out.println("Percentile Values "+i+":"+percentilewindow.get(i));
        				}
        			}
        			numFailRepeate=false;
        			if (MaxActualOps==9999999){
        					duration=7;
        				}
        			//set the MaxActualOps to a value larger than before 
        			if ((tmpMaxActualOps>MaxActualOps)||MaxActualOps==9999999){
        				//MaxActualOps=tmpMaxActualOps+(int)(tmpMaxActualOps*0.1);
        				MaxActualOps=tmpMaxActualOps;
        			}

        			incGap=(int)((MaxActualOps-MaxAcceptedOps)*0.2);
        			if (incGap<20)
        				{incGap=100;}
        			//if (MaxActualOps-MaxAcceptedOps<0){
        			//	System.out.println("FINISHED.................................................................");
        			//}
            		//baserps=prevrpstobebase;        				
    				if (prevAcceptedOps==MaxAcceptedOps){
    					System.out.println("FINISHED....................................................................................................................");
    				}
    				prevAcceptedOps=MaxAcceptedOps;
    				//repeatCnt++;
    				
        			exprps=MaxAcceptedOps;
        			//exprps=10;
        			createRows(exprps,duration);
        			//System.out.println("********************baserps="+baserps+"  exprps="+exprps+"  ****************");
        		}
        		
        	}
	    	timeStart=secFromZero-(int)(xSecMonitor/1000);
	    	timeEnd=timeEnd+duration;
	    	//System.out.println("timeStart:"+timeStart+"timeEnd:"+timeEnd);
	    	numSecPassedCond=0;
	    	tmpMaxActualOps=Collections.max(realOpsLastDuration);
	    	System.out.println("Actual ops within the duration period:"+realOpsLastDuration+"MAX IS: "+tmpMaxActualOps+" ,Median="+percentile(realOpsLastDuration,50));
	    	realOpsLastDuration.clear();
        }
        	
        //System.out.println("************************MAX         : "+MaxActualOps);
        System.out.println("************************MAX Accepted(thrput): "+MaxAcceptedOps);
        //System.out.println("************************prev MAXAcce: "+prevAcceptedOps);
        //System.out.println("************************exprps:       "+exprps);
        System.out.println("Time:"+secFromZero);
        System.out.println("Expected op/per/sec: "+expCnt);
        System.out.println("Actual   Hit/per/sec: "+realHitCnt);
        System.out.println("Actual   Op/per/sec: "+realOpsecCnt);
        System.out.println("25th Percentile    : "+firstQrtl);
        System.out.println("50th Percentile    : "+secQrtl);
        System.out.println("75th Percentile    : "+thirdQrtl);
        System.out.println("90th Percentile    : "+maxQrtl);
        System.out.println("99th Percentile    : "+percentile(values,99));
        System.out.println("AllPercentiles     : "+percentileLatPerSec);
        
        System.out.println("---------------------------------------------------------------"); 
        //loghsn.info("TEST THE LOG---TIME: "+secFromZero);
        //log.info("GUI LOG TEST");
           
   	}

   	
//   	public void anticipatRpsBaseActualRps(double secs) {
//   	
//        //System.out.println("previous sec:"+(long)(secs-1000.0));
//        
//        //System.out.println("Number of requests sent at "+(long)(secs-xSecMonitor)+" which is "+xSecMonitor+" millisecond before: "+freq);
//        int realHitCnt=multiMapStartTimeLatency.get((long)(secs-xSecMonitor)).size();//Actual Hit Count
//
//        //System.out.println("Number of requests sent at "+(long)(secs-xSecMonitor)+" which is "+xSecMonitor+" millisecond before: "+realHitCnt);
//        int realOpsecCnt=multiMapEndTimeLatency.get((long)(secs-xSecMonitor)).size();//Actual op/per/sec
//        
//        //calculateing Percentile
//        //System.out.println("key: "+(long)(secs-xSecMonitor)+" Values: "+multiMapStartTimeLatency.get((long)(secs-xSecMonitor)));
//        List<Integer> values=multiMapEndTimeLatency.get((long)(secs-xSecMonitor));
//        Collections.sort(values);
//        double firstQrtl=percentile(values,25);
//        double secQrtl=percentile(values,50);
//        double thirdQrtl=percentile(values,75);
//        double maxQrtl=percentile(values,90);
//
//        int secFromZero=((int)((secs-xSecMonitor)-startSec)/1000);//converting the secs to value start from 0 not startSec
//        int expCnt=mapTimeOpsec.get(secFromZero);
//        
//        //rpsBaseDecision(realOpsecCnt,expCnt);						//adding to the table based on OPS         
//        //hitBaseDecision(realHitCnt,expCnt);        				//adding to the table based on HitPerSecond
//        //latencyBaseDecision(firstQrtl,secQrtl,thirdQrtl,maxQrtl);   //adding to the table based on Latency
//        realOpsLastDuration.add(realOpsecCnt);
//        latencyAndRpsBaseDecision(realOpsecCnt,expCnt,firstQrtl,secQrtl,thirdQrtl,maxQrtl);   //adding to the table based on Latency&Rps
//        
//        //System.out.println("secFromZero:"+secFromZero+"  timeStart:"+timeStart+" timeEnd:"+timeEnd);
//        if (secFromZero==timeEnd){
//        	//System.out.println("secFromZero==timeEnd"+secFromZero+" = "+timeEnd);
//        	System.out.println("numSecPassedCondexpected:"+(int)((timeEnd-timeStart-(xSecMonitor/1000))*0.5)+" <=numSecPassedCond: "+numSecPassedCond);
//        	if ((int)((timeEnd-timeStart-(xSecMonitor/1000))*0.5)<numSecPassedCond){
//        		prevrpstobebase=exprps+baserps;
//        		exprps=exprps*2;
//            	createRows(exprps+baserps,duration);
//            	//System.out.println("createRow(exprps:"+exprps+"time:"+secFromZero);
//
//        	}
//        	else{//failed
//        		
//        		if (numFailRepeate==true){//two times failed
//        			numFailRepeate=false;
//        			if (baserps==prevrpstobebase){//check to decrease the base
//        				System.out.println("******* based is reduced ********");
//            			baserps=baserps-50;
//            			prevrpstobebase=baserps;
//        			}else{
//            			baserps=prevrpstobebase;        				
//        			}
//
//        			exprps=10;
//        			createRows(exprps+baserps,duration);
//        			System.out.println("********************baserps="+baserps+"  exprps="+exprps+"  ****************");
//        		}
//        		else{//first time failed-reapeat again with the same rps
//            		numFailRepeate=true;
//            		//createRows(exprps+baserps,duration);
//            		createRows(5,duration);
//        		}
//        	}
//	    	timeStart=secFromZero-(int)(xSecMonitor/1000);
//	    	timeEnd=timeEnd+duration;
//	    	//System.out.println("timeStart:"+timeStart+"timeEnd:"+timeEnd);
//	    	numSecPassedCond=0;
//	    	System.out.println("expectec ops within the duration period:"+realOpsLastDuration);
//	    	realOpsLastDuration.clear();
//        }
//        	
//
//        System.out.println("Time:"+secFromZero);
//        System.out.println("Expected op/per/sec: "+expCnt);
//        System.out.println("Actual   Hit/per/sec: "+realHitCnt);
//        System.out.println("Actual   Op/per/sec: "+realOpsecCnt);
//        System.out.println("25th Percentile    : "+firstQrtl);
//        System.out.println("50th Percentile    : "+secQrtl);
//        System.out.println("75th Percentile    : "+thirdQrtl);
//        System.out.println("90th Percentile    : "+maxQrtl);
//        System.out.println("99th Percentile    : "+percentile(values,99));
//        System.out.println("---------------------------------------------------------------"); 
//        //loghsn.info("TEST THE LOG---TIME: "+secFromZero);
//        //log.info("GUI LOG TEST");
//           
//   	}
    //end of hsn
   	
    private synchronized void checkNextSecond(double secs) {
        // next second
    	//hitCnt++;		to be deleted
    	
        if (time == secs) {
            return;
        }

        if (startSec == 0) {
            startSec = secs;
        }
        time = secs;

        anticipatRpsBaseActualRps1(secs); //hsn
        
        double nextRps = getRPSForSecond((secs - startSec) / 1000);
        if (nextRps < 0) {
            stopping = true;
            rps = rps > 0 ? rps * (stopTries > 10 ? 2 : 1) : 1;
            stopTest();
            notifyAll();
        } else {
            rps = nextRps;
        }

        if (log.isDebugEnabled()) {
            log.debug("Second changed " + ((secs - startSec) / 1000) + ", sleeping: " + cntDelayed + " sent " + cntSent + " RPS: " + rps);
        }

        if (cntDelayed < 1) {
            log.warn("No free threads left in worker pool, made  " + cntSent + '/' + rps + " samples");
        }

        cntSent = 0;
        msecPerReq = 1000d / rps;

    }

    private int getDelay(long msecs) {
        //log.info("Calculating "+msecs + " " + cntSent * msecPerReq+" "+cntSent);
        if (msecs < (cntSent * msecPerReq)) {
            int delay = 1 + (int) (1000.0 * (cntDelayed + 1) / (double) rps);
            return delay;
        }
        return 0;
    }

    void setData(CollectionProperty rows) {
        setProperty(rows);
    }

    JMeterProperty getData() {
        if (overrideProp != null) {
            return overrideProp;
        }
        return getProperty(DATA_PROPERTY);
    }

    //hsn	
    private static List<Object> getArrayListForArray(Object[] rowData) {
        ArrayList<Object> res = new ArrayList<Object>();
        for (int n = 0; n < rowData.length; n++) // note that we MUST use ArrayList
        {
            res.add(rowData[n]);
        }

        return res;
    }
    
    
    public synchronized void  createRows(int rpsTodo,int durTodo) {
    	
    	Object[] rowData = new Object[3];
	    //for (int i = 0; i < rowData.length; i++) {
	    rowData[0] = (Object)rpsTodo;
	    rowData[1] = (Object)rpsTodo;
	    rowData[2] = (Object)durTodo;
	    //}
	    
	    List<Object> item=getArrayListForArray(rowData);
	    
	
	    rows.addItem(item);
	    int st=mapTimeOpsec.size();
	    for (int i=st;i<st+durTodo;i++){
	    	mapTimeOpsec.put(i+1, rpsTodo); //from and to rps are the same change the code if they are different
	    	//System.out.println("time: "+(i+1)+" operation per second: "+mapTimeOpsec.get(i+1));
	    }
	    //rowData[0] = (Object)30;
	    //rowData[1] = (Object)30;
	    //rowData[2] = (Object)10;
	    //item=getArrayListForArray(rowData);
	    //rows.addItem(item);
	    System.out.println("Item:"+item.toString());
    }
    
    //////////////////////////////
    
    public double getRPSForSecond(double sec) {
        //JMeterProperty data = getData();
        
        //if (data instanceof NullProperty) return -1;
        //CollectionProperty rows = (CollectionProperty) data;
        PropertyIterator scheduleIT = rows.iterator();

        while (scheduleIT.hasNext()) {
            ArrayList<Object> curProp = (ArrayList<Object>) scheduleIT.next().getObjectValue();

            int duration = getIntValue(curProp, DURATION_FIELD_NO);
            double from = getDoubleValue(curProp, FROM_FIELD_NO);
            double to = getDoubleValue(curProp, TO_FIELD_NO);
            //log.debug("sec "+sec+" Dur: "+duration+" from "+from+" to "+to);
            if (sec - duration <= 0) {
            	double rpsCalculated = from + (int) (sec * ((to - from) / (double) duration));
                //log.debug("RPS: "+rps);
                return rpsCalculated;
            } else {
                sec -= duration;
            }
        }
        return -1;
    }

    private double getDoubleValue(ArrayList<Object> prop, int colID) throws NumberFormatException {
        JMeterProperty val = (JMeterProperty) prop.get(colID);
        return val.getDoubleValue();
    }
    
    private int getIntValue(ArrayList<Object> prop, int colID) throws NumberFormatException {
        JMeterProperty val = (JMeterProperty) prop.get(colID);
        return val.getIntValue();
    }

    private void trySettingLoadFromProperty() {
        String loadProp = JMeterUtils.getProperty(DATA_PROPERTY);
        log.debug("Load prop: " + loadProp);
        if (loadProp != null && loadProp.length() > 0) {
            log.info("GUI load profile will be ignored");
            PowerTableModel dataModel = new PowerTableModel(VariableThroughputTimer.columnIdentifiers, VariableThroughputTimer.columnClasses);

            String[] chunks = loadProp.split("\\)");

            for (int c = 0; c < chunks.length; c++) {
                try {
                    parseChunk(chunks[c], dataModel);
                } catch (RuntimeException e) {
                    log.warn("Wrong load chunk ignored: " + chunks[c], e);
                }
            }

            log.info("Setting load profile from property " + DATA_PROPERTY + ": " + loadProp);
            overrideProp = JMeterPluginsUtils.tableModelRowsToCollectionProperty(dataModel, VariableThroughputTimer.DATA_PROPERTY);
        }
    }

    private static void parseChunk(String chunk, PowerTableModel model) {
        log.debug("Parsing chunk: " + chunk);
        String[] parts = chunk.split("[(,]");
        String loadVar = parts[0].trim();

        if (loadVar.equalsIgnoreCase("const")) {
            int const_load = Integer.parseInt(parts[1].trim());
            Integer[] row = new Integer[3];
            row[FROM_FIELD_NO] = const_load;
            row[TO_FIELD_NO] = const_load;
            row[DURATION_FIELD_NO] = JMeterPluginsUtils.getSecondsForShortString(parts[2]);
            model.addRow(row);

        } else if (loadVar.equalsIgnoreCase("line")) {
            Integer[] row = new Integer[3];
            row[FROM_FIELD_NO] = Integer.parseInt(parts[1].trim());
            row[TO_FIELD_NO] = Integer.parseInt(parts[2].trim());
            row[DURATION_FIELD_NO] = JMeterPluginsUtils.getSecondsForShortString(parts[3]);
            model.addRow(row);

        } else if (loadVar.equalsIgnoreCase("step")) {
            // FIXME: float (from-to)/inc will be stepped wrong
            int from = Integer.parseInt(parts[1].trim());
            int to = Integer.parseInt(parts[2].trim());
            int inc = Integer.parseInt(parts[3].trim()) * (from > to ? -1 : 1);
            //log.info(from + " " + to + " " + inc);
            for (int n = from; (inc > 0 ? n <= to : n > to); n += inc) {
                //log.info(" " + n);
                Integer[] row = new Integer[3];
                row[FROM_FIELD_NO] = n;
                row[TO_FIELD_NO] = n;
                row[DURATION_FIELD_NO] = JMeterPluginsUtils.getSecondsForShortString(parts[4]);
                model.addRow(row);
            }

        } else {
            throw new RuntimeException("Unknown load type: " + parts[0]);
        }
    }

    // TODO: resolve shutdown problems. Patch JMeter if needed
    // TODO: make something with test stopping in JMeter. Write custom plugin that tries to kill all threads? Guillotine Stopper! 
    protected void stopTest() {
        if (stopTries > 30) {
            throw new RuntimeException("More than 30 seconds - stopping by exception");
        }

        if (lastStopTry == time) {
            return;
        }
        log.info("No further RPS schedule, asking threads to stop...");
        lastStopTry = time;
        stopTries++;
        if (stopTries > 10) {
            log.info("Tries more than 10, stop it NOW!");
            StandardJMeterEngine.stopEngineNow();
        } else if (stopTries > 5) {
            log.info("Tries more than 5, stop it!");
            StandardJMeterEngine.stopEngine();
        } else {
            JMeterContextService.getContext().getEngine().askThreadsToStop();
        }
    }

    @Override
    public void testStarted() {
        stopping = false;
        stopTries = 0;
    }

    @Override
    public void testStarted(String string) {
        testStarted();
    }

    @Override
    public void testEnded() {
    }

    @Override
    public void testEnded(String string) {
        testEnded();
    }

    @Override
    public void testIterationStart(LoopIterationEvent lie) {
    }
}
