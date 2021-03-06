package net.explorviz.model.helper;

import org.eclipse.xtend.lib.annotations.Accessors
import java.util.List
import java.util.ArrayList
import net.explorviz.model.Communication
import com.github.jasminb.jsonapi.annotations.Relationship

import com.github.jasminb.jsonapi.annotations.Type

@Type("communicationtileaccumulator")
class CommunicationTileAccumulator extends DrawEdgeEntity {
	@Accessors int requestsCache
	
	@Accessors val transient List<Communication> communications = new ArrayList<Communication>(4)
	@Accessors transient boolean alreadyDrawn = false
	 
	@Accessors Point startPoint 
	@Accessors Point endPoint 
	
	@Relationship("parent")
	@Accessors CommunicationAccumulator parent

}
