/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.isis.viewer.json.viewer.resources.domainobjects;

import org.apache.isis.core.metamodel.adapter.ObjectAdapter;
import org.apache.isis.core.metamodel.consent.Consent;
import org.apache.isis.core.metamodel.facetapi.Facet;
import org.apache.isis.core.metamodel.spec.ObjectSpecification;
import org.apache.isis.core.metamodel.spec.feature.ObjectMember;
import org.apache.isis.viewer.json.applib.JsonRepresentation;
import org.apache.isis.viewer.json.applib.RepresentationType;
import org.apache.isis.viewer.json.viewer.ResourceContext;
import org.apache.isis.viewer.json.viewer.representations.LinkFollower;
import org.apache.isis.viewer.json.viewer.representations.Rel;
import org.apache.isis.viewer.json.viewer.representations.ReprRendererAbstract;
import org.codehaus.jackson.node.NullNode;

public abstract class AbstractObjectMemberReprRenderer<R extends ReprRendererAbstract<R, ObjectAndMember<T>>, T extends ObjectMember> 
        extends ReprRendererAbstract<R, ObjectAndMember<T>> {

    protected enum Mode {
        INLINE,
        FOLLOWED,
        STANDALONE;

        public boolean isInline() {
            return this == INLINE;
        }
        public boolean isFollowed() {
            return this == FOLLOWED;
        }
        public boolean isStandalone() {
            return this == STANDALONE;
        }
    }
    
    protected ObjectAdapterLinkTo linkTo;
    
    protected ObjectAdapter objectAdapter;
    protected MemberType memberType;
    protected T objectMember;
    protected Mode mode = Mode.INLINE; // unless we determine otherwise
    
    public AbstractObjectMemberReprRenderer(ResourceContext resourceContext, LinkFollower linkFollower, RepresentationType representationType, JsonRepresentation representation) {
        super(resourceContext, linkFollower, representationType, representation);
    }
    
    @Override
    public R with(ObjectAndMember<T> objectAndMember) {
        this.objectAdapter = objectAndMember.getObjectAdapter();
        this.objectMember = objectAndMember.getMember();
        this.memberType = MemberType.determineFrom(objectMember);
        usingLinkTo(new DomainObjectLinkTo());

        // done eagerly so can use as criteria for x-ro-follow-links
        representation.mapPut(memberType.getJsProp(), objectMember.getId());
        representation.mapPut("memberType", memberType.getName());

        return cast(this);
    }

    /**
     * Must be called after {@link #with(ObjectAndMember)} (which provides the {@link #objectAdapter}).
     */
    public R usingLinkTo(ObjectAdapterLinkTo linkTo) {
        this.linkTo = linkTo.usingResourceContext(resourceContext).with(objectAdapter);
        return cast(this);
    }


    /**
     * Indicate that this is a standalone representation. 
     */
    public R asStandalone() {
        mode = Mode.STANDALONE;
        return cast(this);
    }

    /**
     * Indicate that this is a representation to include as the result of a followed link. 
     */
    public R asFollowed() {
        mode = Mode.FOLLOWED;
        return cast(this);
    }

    /**
     * For subclasses to call from their {@link #render()} method.
     */
    protected void renderMemberContent() {
        if(mode.isInline()) {
            addDetailsLinkIfPersistent();
        } 
        
        if (mode.isStandalone()){
            addLinkToSelf();
            addLinkToUp();
        }
        
        if (mode.isFollowed() || mode.isStandalone()){
            addMutatorsIfEnabled();
            
            putExtensionsIsisProprietary();
            addLinksToFormalDomainModel();
            addLinksIsisProprietary();
        }
    }
    

    private void addLinkToSelf() {
        getLinks().arrayAdd(linkTo.memberBuilder(Rel.SELF, memberType, objectMember).build());
    }

    private void addLinkToUp() {
        getLinks().arrayAdd(linkTo.builder(Rel.UP).build());
    }
    
    protected abstract void addMutatorsIfEnabled();
    
    /**
     * For subclasses to call back to when {@link #addMutatorsIfEnabled() adding mutators}.
     */
    protected void addLinkFor(final MutatorSpec mutatorSpec) {
        if(!hasMemberFacet(mutatorSpec.mutatorFacetType)) {
            return;
        } 
        JsonRepresentation arguments = mutatorArgs(mutatorSpec);
        RepresentationType representationType = memberType.getRepresentationType();
        JsonRepresentation mutatorLink = 
                linkToForMutatorInvoke().memberBuilder(mutatorSpec.rel, memberType, objectMember, representationType, mutatorSpec.suffix)
                .withHttpMethod(mutatorSpec.httpMethod)
                .withArguments(arguments)
                .build();
        getLinks().arrayAdd(mutatorLink);
    }

    /**
     * Hook to allow actions to render invoke links that point to the contributing service.
     */
    protected ObjectAdapterLinkTo linkToForMutatorInvoke() {
        return linkTo;
    }

    /**
     * Default implementation (common to properties and collections) that can 
     * be overridden (ie by actions) if required.
     */
    protected JsonRepresentation mutatorArgs(MutatorSpec mutatorSpec) {
        if(mutatorSpec.arguments.isNone()) {
            return null;
        }
        if(mutatorSpec.arguments.isOne()) {
            final JsonRepresentation repr = JsonRepresentation.newMap();
            repr.mapPut("value", NullNode.getInstance()); // force a null into the map
            return repr;
        }
        // overridden by actions
        throw new UnsupportedOperationException("override mutatorArgs() to populate for many arguments");
    }
    
    private void addDetailsLinkIfPersistent() {
        if(!objectAdapter.isPersistent()) {
            return;
        }
        final JsonRepresentation link = 
                linkTo.memberBuilder(Rel.DETAILS, memberType, objectMember).build();
        getLinks().arrayAdd(link);
        
        final LinkFollower membersLinkFollower = getLinkFollower();
        final LinkFollower detailsLinkFollower = membersLinkFollower.follow("links[rel=%s]", Rel.DETAILS.getName());
        if(membersLinkFollower.matches(representation) && detailsLinkFollower.matches(link)) {
            followDetailsLink(link);
        }
        return;
    }

    protected abstract void followDetailsLink(JsonRepresentation detailsLink);

    protected final void putDisabledReasonIfDisabled() {
        String disabledReasonRep = usability().getReason();
        representation.mapPut("disabledReason", disabledReasonRep);
    }

    protected abstract void putExtensionsIsisProprietary();
    protected abstract void addLinksToFormalDomainModel();
    protected abstract void addLinksIsisProprietary();

    /**
     * Convenience method.
     */
    public boolean isMemberVisible() {
        return visibility().isAllowed();
    }

    protected <F extends Facet> F getMemberSpecFacet(Class<F> facetType) {
        ObjectSpecification otoaSpec = objectMember.getSpecification();
        return otoaSpec.getFacet(facetType);
    }

    protected boolean hasMemberFacet(Class<? extends Facet> facetType) {
        return objectMember.getFacet(facetType) != null;
    }

    protected Consent usability() {
        return objectMember.isUsable(getSession(), objectAdapter);
    }

    protected Consent visibility() {
        return objectMember.isVisible(getSession(), objectAdapter);
    }

    
}