package policy;

import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;

import flow.types.FlowType;

public class GpsSmsSpec implements PolicySpec {
    private static final Set<String> gpsEntryMethods = new HashSet<String>();
    private static final Set<String> gpsSrcMethods = new HashSet<String>();
    private static final Set<String> smsDstMethods = new HashSet<String>();
    
    public GpsSmsSpec() {
        gpsEntryMethods.add("onNmeaReceived");
        gpsEntryMethods.add("onGpsStatusChanged");
        gpsEntryMethods.add("onLocationChanged");
        gpsEntryMethods.add("onStatusChanged");
        gpsEntryMethods.add("onProviderDisabled");
        gpsEntryMethods.add("onProviderEnabled");

        gpsSrcMethods.add("requestLocationUpdates");
        gpsSrcMethods.add("getProviders");
        gpsSrcMethods.add("requestSingleUpdate");
        gpsSrcMethods.add("getProvider");
        gpsSrcMethods.add("getLastKnownLocation");
        gpsSrcMethods.add("isProviderEnabled");
        gpsSrcMethods.add("addProximityAlert");
        gpsSrcMethods.add("requestLocationUpdates");
        gpsSrcMethods.add("getBestProvider");
        gpsSrcMethods.add("getNeighboringCellInfo");
        gpsSrcMethods.add("getCellLocation");
        gpsSrcMethods.add("getProviders");
        gpsSrcMethods.add("requestLocationUpdates");
        gpsSrcMethods.add("requestLocationUpdates");
        gpsSrcMethods.add("sendExtraCommand");
        gpsSrcMethods.add("addNmeaListener");
        gpsSrcMethods.add("requestSingleUpdate");
        gpsSrcMethods.add("requestSingleUpdate");
        gpsSrcMethods.add("addGpsStatusListener");
        gpsSrcMethods.add("requestSingleUpdate");
        gpsSrcMethods.add("requestLocationUpdates");

        smsDstMethods.add("sendTextMessage");
        smsDstMethods.add("sendMultipartTextMessage");
        smsDstMethods.add("sendDataMessage");
    }

    @Override
    public boolean allowed(FlowType src, FlowType dst) {
        BasicBlockInContext srcBlock = src.getBlock();
        SSAInstruction srcInst = srcBlock.getLastInstruction();
        BasicBlockInContext dstBlock = dst.getBlock();
        SSAInstruction dstInst = dstBlock.getLastInstruction();
        boolean srcIsGps = false;
        boolean destIsSms = false;
        
        if(srcBlock.isEntryBlock()) {
            String methName = srcBlock.getNode().getMethod().getName().toString();
            if(gpsEntryMethods.contains(methName)) srcIsGps = true;
        } else if(srcInst instanceof SSAInvokeInstruction) {
            SSAInvokeInstruction srcInv = (SSAInvokeInstruction)srcInst;
            String tgtName = srcInv.getDeclaredTarget().getName().toString();
            if(gpsSrcMethods.contains(tgtName)) srcIsGps = true;
        }
        if(dstInst instanceof SSAInvokeInstruction) {
            SSAInvokeInstruction dstInv = (SSAInvokeInstruction)dstInst;
            String tgtName = dstInv.getDeclaredTarget().getName().toString();
            if(smsDstMethods.contains(tgtName)) destIsSms = true;
        }
        return srcIsGps && destIsSms;
    }
}
