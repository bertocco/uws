/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2009.                            (c) 2009.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits réservés
*                                       
*  NRC disclaims any warranties,        Le CNRC dénie toute garantie
*  expressed, implied, or               énoncée, implicite ou légale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           être tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou général,
*  arising from the use of the          accessoire ou fortuit, résultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        être utilisés pour approuver ou
*  products derived from this           promouvoir les produits dérivés
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  préalable et particulière
*                                       par écrit.
*                                       
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*                                       
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la “GNU Affero General Public
*  License as published by the          License” telle que publiée
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (à votre gré)
*  any later version.                   toute version ultérieure.
*                                       
*  OpenCADC is distributed in the       OpenCADC est distribué
*  hope that it will be useful,         dans l’espoir qu’il vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans même la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
*  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           Générale Publique GNU Affero
*  more details.                        pour plus de détails.
*                                       
*  You should have received             Vous devriez avoir reçu une
*  a copy of the GNU Affero             copie de la Licence Générale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 4 $
*
************************************************************************
*/

package ca.nrc.cadc.uws.web.restlet.resources;


import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.restlet.resource.Post;
import org.restlet.representation.Representation;
import org.restlet.data.Form;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.*;
import java.text.ParseException;

import ca.nrc.cadc.uws.*;
import ca.nrc.cadc.uws.web.restlet.JobAssembler;
import ca.nrc.cadc.uws.web.WebRepresentationException;
import java.net.MalformedURLException;


/**
 * Resource to handle Asynchronous calls.
 */
public class AsynchResource extends UWSResource
{
    private final static Logger LOGGER = Logger.getLogger(UWSResource.class);


    /**
     * Accept POST requests.
     *
     * @param entity    The POST Request body.
     */
    @Post
    public void accept(final Representation entity)
    {
        final Form form = new Form(entity);
        final Map<String, String> errors = validate(form);
        
        if (!errors.isEmpty())
        {
            generateErrorRepresentation(errors);
            return;
        }

        final Job job;

        try
        {
            final JobAssembler jobAssembler = new JobAssembler(form);
            job = jobAssembler.assemble();
        }
        catch (ParseException e)
        {
            LOGGER.error("Unable to create Job! ", e);
            throw new WebRepresentationException("Unable to create Job!", e);
        }
        catch (MalformedURLException e)
        {
            LOGGER.error("The Error URL is invalid.", e);
            throw new WebRepresentationException("The Error URL is invalid.",
                                                 e);
        }

        final Job persistedJob = getJobManager().persist(job);
        redirectSeeOther(getHostPart() + "/async/" + persistedJob.getJobId());
    }
    
    /**
     * Assemble the XML for this Resource's Representation into the given
     * Document.
     *
     * @param document The Document to build up.
     * @throws java.io.IOException If something went wrong or the XML cannot be
     *                             built.
     */
    protected void buildXML(final Document document) throws IOException
    {
        final Element jobsElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.JOBS.getAttributeName());

        jobsElement.setAttribute("xmlns:xlink",
                                 "http://www.w3.org/1999/xlink");
        jobsElement.setAttribute("xmlns:xsi",
                                 "http://www.w3.org/2001/XMLSchema-instance");

        jobsElement.setPrefix(XML_NAMESPACE_PREFIX);

        for (final Job job : getJobs())
        {
            final Element jobRefElement =
                    document.createElementNS(XML_NAMESPACE_URI,
                                             JobAttribute.JOB_REF.
                                                     getAttributeName());
            
            jobRefElement.setPrefix(XML_NAMESPACE_PREFIX);
            jobRefElement.setAttribute("id", job.getJobId());
            jobRefElement.setAttribute("xlink:href",
                                       getHostPart() + "/async/"
                                       + job.getJobId());
            
            final Element jobRefPhaseElement =
                    document.createElementNS(XML_NAMESPACE_URI,
                                             JobAttribute.EXECUTION_PHASE.
                                                     getAttributeName());
            jobRefPhaseElement.setPrefix(XML_NAMESPACE_PREFIX);
            jobRefPhaseElement.setTextContent(job.getExecutionPhase().name());

            jobRefElement.appendChild(jobRefPhaseElement);
            jobsElement.appendChild(jobRefElement);
        }

        document.appendChild(jobsElement);
    }

    /**
     * Obtain all of the Jobs available to list to the client.
     *
     * @return      List of Job objects.
     */
    protected List<Job> getJobs()
    {
        return new ArrayList<Job>(getJobManager().getJobs());
    }
}
