/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.process.geoloc;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.Quantity;
import net.opengis.swe.v20.Vector;
import org.sensorhub.algo.geoloc.Ellipsoid;
import org.sensorhub.algo.geoloc.EllipsoidIntersect;
import org.sensorhub.algo.vecmath.Vect3d;
import org.sensorhub.api.processing.OSHProcessInfo;
import org.vast.process.ExecutableProcessImpl;
import org.vast.process.ProcessException;
import org.vast.process.ProcessInfo;
import org.vast.swe.SWEConstants;
import org.vast.swe.helper.GeoPosHelper;


/**
 * <p>
 * Computes intersection of a 3D ray with an ellipsoid which axes are
 * aligned with the axes of the referential of the ray. This process outputs
 * coordinates of the intersect point expressed in the same frame (e.g. ECEF).
 * <br/>This version allows for height adjustment which means that the
 * intersection is computed with a virtual ellipsoid that can be above or
 * below the reference WGS84 ellipsoid.
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Nov 13, 2015
 */
public class RayIntersectEllipsoid extends ExecutableProcessImpl
{
    public static final OSHProcessInfo INFO = new OSHProcessInfo("RayIntersectEllipsoid", "Ray Ellipsoid Intersection", "Compute 3D intersection between a ray and an ellipsoid", RayIntersectEllipsoid.class);
    
    protected Vector rayOrigin;
    protected Vector rayDirection;
    protected Vector intersection;
    protected Quantity heightAdjustment;
    protected EllipsoidIntersect rie;
    protected Vect3d origin;
    protected Vect3d dir;
    protected Vect3d intersect;
    

    public RayIntersectEllipsoid()
    {
        this(INFO);
    }
    
    
    public RayIntersectEllipsoid(ProcessInfo info)
    {
        super(info);
        GeoPosHelper sweHelper = new GeoPosHelper();
        
        //// INPUTS ////
        // ray origin in reference frame (ECEF by default)
        rayOrigin = sweHelper.newLocationVectorECEF(null);
        inputData.add("rayOrigin", rayOrigin);
        
        // ray direction in reference frame (ECEF by default)
        rayDirection = sweHelper.newUnitVectorXYZ(null, SWEConstants.REF_FRAME_ECEF);
        inputData.add("rayDirection", rayDirection);
        
        //// PARAMETERS ////
        heightAdjustment = sweHelper.newQuantity(null, "Ellipsoid Height Adjustment", "Ellipsoid height offset used when computing the intersection", "m");
        heightAdjustment.assignNewDataBlock();
        paramData.add("heightAdjustment", heightAdjustment);
                
        //// OUTPUTS ////
        intersection = sweHelper.newLocationVectorECEF(null);
        outputData.add("intersection", intersection);
    }

    
    @Override
    public void init() throws ProcessException
    {
        super.init();
        
        this.origin = new Vect3d();
        this.dir = new Vect3d();
        this.intersect = new Vect3d();
        
        // instantiate ellipsoid intersection algorithm
        double heightOffset = heightAdjustment.getData().getDoubleValue();
        this.rie = new EllipsoidIntersect(Ellipsoid.WGS84, heightOffset);
    }
    
    
    @Override
    public void execute() throws ProcessException
    {
        // get ray origin input
        DataBlock originData = rayOrigin.getData();
        origin.x = originData.getDoubleValue(0);
        origin.y = originData.getDoubleValue(1);
        origin.z = originData.getDoubleValue(2);
        
        // get ray direction input
        DataBlock dirData = rayDirection.getData();
        dir.x = dirData.getDoubleValue(0);
        dir.y = dirData.getDoubleValue(1);
        dir.z = dirData.getDoubleValue(2);
        
        boolean ok = rie.computeIntersection(origin, dir, intersect);
        if (!ok)
            getLogger().debug("No intersection found");
        
        // set intersection point output
        DataBlock intersectData = intersection.getData();
        intersectData.setDoubleValue(0, intersect.x);
        intersectData.setDoubleValue(1, intersect.y);
        intersectData.setDoubleValue(2, intersect.z);
    }
}
