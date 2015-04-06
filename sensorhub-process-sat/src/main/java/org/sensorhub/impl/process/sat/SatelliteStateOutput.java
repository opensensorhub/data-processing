/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are Copyright (C) 2014 Sensia Software LLC.
 All Rights Reserved.
 
 Contributor(s): 
    Alexandre Robin <alex.robin@sensiasoftware.com>
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.process.sat;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataRecord;
import org.sensorhub.api.common.IEventHandler;
import org.sensorhub.api.common.IEventListener;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.api.data.IStreamingDataInterface;
import org.sensorhub.api.module.IModule;
import org.sensorhub.impl.common.BasicEventHandler;
import org.vast.physics.MechanicalState;
import org.vast.physics.OrbitPredictor;
import org.vast.physics.TLEOrbitPredictor;
import org.vast.swe.SWEConstants;
import org.vast.swe.SWEHelper;


/**
 * <p>
 * Satellite State Vector Output (position + velocity)
 * </p>
 *
 * <p>Copyright (c) 2014 Sensia Software LLC</p>
 * @author Alexandre Robin <alex.robin@sensiasoftware.com>
 * @since Apr 5, 2015
 */
public class SatelliteStateOutput implements IStreamingDataInterface
{
    TLEPredictorProcess parentProcess;
    IEventHandler eventHandler;
    DataComponent outputDef;
    DataEncoding outputEncoding;    
    DataBlock lastRecord;
    long lastRecordTime = Long.MIN_VALUE;
    
    String name;
    Timer timer = new Timer();
    TimerTask posUpdateTask;
    OrbitPredictor orbitPredictor;
    double realTimePosDt = 1.0;
    double predictedPosDt = 600.0;
    

    public SatelliteStateOutput(TLEPredictorProcess parentProcess, String name, TLEOutput tleOutput)
    {
        this.parentProcess = parentProcess;
        this.eventHandler = new BasicEventHandler();
        this.orbitPredictor = new TLEOrbitPredictor(tleOutput);
        this.name = name;
        
        // create output structure
        SWEHelper fac = new SWEHelper();
        DataRecord rec = fac.newDataRecord();
        rec.setName(getName());
        rec.setDefinition(SWEConstants.DEF_PLATFORM_LOC.replace("Location", "State"));
        rec.addField("time", fac.newTimeStampIsoUTC());
        rec.addField("position", fac.newLocationVectorECEF(SWEConstants.DEF_PLATFORM_LOC));
        rec.addField("velocity", fac.newVelocityVectorECEF(SWEConstants.DEF_PLATFORM_LOC.replace("Location", "Velocity"), "m/s"));
        this.outputDef = rec;
        
        this.outputEncoding = fac.newTextEncoding();
    }
    
    
    protected void start()
    {
        // start task to compute new satellite position
        posUpdateTask = new TimerTask() {
            @Override
            public void run()
            {
                computeAndOutputState(scheduledExecutionTime() / 1000.0);              
            }            
        };
        
        long outputDtInMillis = (long)(realTimePosDt * 1000L);
        long firstTime = (System.currentTimeMillis() / outputDtInMillis + 1) * outputDtInMillis;
        timer.scheduleAtFixedRate(posUpdateTask, new Date(firstTime), outputDtInMillis); // every dT
    }
    
    
    protected void stop()
    {
        if (posUpdateTask != null)
            posUpdateTask.cancel();
    }
    
    
    protected void computeAndOutputState(double time)
    {
        MechanicalState state = orbitPredictor.getECFState(time);
        
        // send to output
        DataBlock stateData = (lastRecord == null) ? outputDef.createDataBlock() : lastRecord.renew();
        stateData.setDoubleValue(0, state.julianTime);
        stateData.setDoubleValue(1, state.linearPosition.x);
        stateData.setDoubleValue(2, state.linearPosition.y);
        stateData.setDoubleValue(3, state.linearPosition.z);
        stateData.setDoubleValue(4, state.linearVelocity.x);
        stateData.setDoubleValue(5, state.linearVelocity.y);
        stateData.setDoubleValue(6, state.linearVelocity.z);
        
        lastRecordTime = System.currentTimeMillis();
        lastRecord = stateData;
        eventHandler.publishEvent(new DataEvent(lastRecordTime, this, lastRecord));
    }
    
    
    protected void computeAndOutputPredictedTrajectory(double startTime, double stopTime)
    {
        startTime += predictedPosDt;
        for (double time = startTime; time < stopTime; time += predictedPosDt)
            computeAndOutputState(time);
    }


    @Override
    public IModule<?> getParentModule()
    {
        return parentProcess;
    }


    @Override
    public String getName()
    {
        return name;
    }


    @Override
    public boolean isEnabled()
    {
        return true;
    }
    
    
    @Override
    public DataComponent getRecordDescription()
    {
        return outputDef;
    }


    @Override
    public DataEncoding getRecommendedEncoding()
    {
        return outputEncoding;
    }


    @Override
    public DataBlock getLatestRecord()
    {
        return lastRecord;
    }


    @Override
    public long getLatestRecordTime()
    {
        return lastRecordTime;
    }


    @Override
    public double getAverageSamplingPeriod()
    {
        return realTimePosDt;
    }
    
    
    @Override
    public void registerListener(IEventListener listener)
    {
        eventHandler.registerListener(listener);
    }


    @Override
    public void unregisterListener(IEventListener listener)
    {
        eventHandler.unregisterListener(listener);
    }

}
