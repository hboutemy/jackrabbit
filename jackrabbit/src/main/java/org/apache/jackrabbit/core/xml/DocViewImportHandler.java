/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core.xml;

import org.apache.jackrabbit.core.NodeId;
import org.apache.jackrabbit.name.NameException;
import org.apache.jackrabbit.name.NameFormat;
import org.apache.jackrabbit.name.QName;
import org.apache.jackrabbit.util.ISO9075;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Stack;

/**
 * <code>DocViewImportHandler</code> processes Document View XML SAX events
 * and 'translates' them into <code>{@link Importer}</code> method calls.
 */
class DocViewImportHandler extends TargetImportHandler {

    private static Logger log = LoggerFactory.getLogger(DocViewImportHandler.class);

    /**
     * stack of NodeInfo instances; an instance is pushed onto the stack
     * in the startElement method and is popped from the stack in the
     * endElement method.
     */
    private final Stack stack = new Stack();
    // buffer used to merge adjacent character data
    private BufferedStringValue textHandler = null;

    /**
     * Constructs a new <code>DocViewImportHandler</code>.
     *
     * @param importer
     */
    DocViewImportHandler(Importer importer) {
        super(importer);
    }

    /**
     * Appends the given character data to the internal buffer.
     *
     * @param ch     the characters to be appended
     * @param start  the index of the first character to append
     * @param length the number of characters to append
     * @throws SAXException if an error occurs
     * @see #characters(char[], int, int)
     * @see #ignorableWhitespace(char[], int, int)
     * @see #processCharacters()
     */
    private void appendCharacters(char[] ch, int start, int length)
            throws SAXException {
        if (textHandler == null) {
            textHandler = new BufferedStringValue(nsContext);
        }
        try {
            textHandler.append(ch, start, length);
        } catch (IOException ioe) {
            String msg = "internal error while processing internal buffer data";
            log.error(msg, ioe);
            throw new SAXException(msg, ioe);
        }
    }

    /**
     * Translates character data reported by the
     * <code>{@link #characters(char[], int, int)}</code> &
     * <code>{@link #ignorableWhitespace(char[], int, int)}</code> SAX events
     * into a  <code>jcr:xmltext</code> child node with one
     * <code>jcr:xmlcharacters</code> property.
     *
     * @throws SAXException if an error occurs
     * @see #appendCharacters(char[], int, int)
     */
    private void processCharacters()
            throws SAXException {
        try {
            if (textHandler != null && textHandler.length() > 0) {
                // there is character data that needs to be added to
                // the current node

                // check for pure whitespace character data
                Reader reader = textHandler.reader();
                try {
                    int ch;
                    while ((ch = reader.read()) != -1) {
                        if (ch > 0x20) {
                            break;
                        }
                    }
                    if (ch == -1) {
                        // the character data consists of pure whitespace, ignore
                        log.debug("ignoring pure whitespace character data...");
                        // reset handler
                        textHandler.dispose();
                        textHandler = null;
                        return;
                    }
                } finally {
                    reader.close();
                }

                NodeInfo node =
                        new NodeInfo(QName.JCR_XMLTEXT, null, null, null);
                TextValue[] values =
                        new TextValue[]{textHandler};
                ArrayList props = new ArrayList();
                PropInfo prop = new PropInfo(
                        QName.JCR_XMLCHARACTERS, PropertyType.STRING, values);
                props.add(prop);
                // call Importer
                importer.startNode(node, props);
                importer.endNode(node);

                // reset handler
                textHandler.dispose();
                textHandler = null;
            }
        } catch (IOException ioe) {
            String msg = "internal error while processing internal buffer data";
            log.error(msg, ioe);
            throw new SAXException(msg, ioe);
        } catch (RepositoryException re) {
            throw new SAXException(re);
        }
    }

    //-------------------------------------------------------< ContentHandler >

    /**
     * {@inheritDoc}
     * <p/>
     * See also {@link DocViewSAXEventGenerator#leavingProperties(javax.jcr.Node, int)}
     * regarding special handling of multi-valued properties on export.
     */
    public void startElement(String namespaceURI, String localName,
                             String qName, Attributes atts)
            throws SAXException {
        // process buffered character data
        processCharacters();

        try {
            QName nodeName = new QName(namespaceURI, localName);
            // decode node name
            nodeName = ISO9075.decode(nodeName);

            // properties
            NodeId id = null;
            QName nodeTypeName = null;
            QName[] mixinTypes = null;

            ArrayList props = new ArrayList(atts.getLength());
            for (int i = 0; i < atts.getLength(); i++) {
                QName propName = new QName(atts.getURI(i), atts.getLocalName(i));
                // decode property name
                propName = ISO9075.decode(propName);

                // value(s)
                String attrValue = atts.getValue(i);
                TextValue[] propValues;

                // always assume single-valued property for the time being
                // until a way of properly serializing/detecting multi-valued
                // properties on re-import is found (see JCR-325);
                // see also DocViewSAXEventGenerator#leavingProperties(Node, int)
                // todo proper multi-value serialization support
                propValues = new TextValue[1];
                propValues[0] = new StringValue(attrValue, nsContext);

                if (propName.equals(QName.JCR_PRIMARYTYPE)) {
                    // jcr:primaryType
                    if (attrValue.length() > 0) {
                        try {
                            nodeTypeName = NameFormat.parse(attrValue, nsContext);
                        } catch (NameException ne) {
                            throw new SAXException("illegal jcr:primaryType value: "
                                    + attrValue, ne);
                        }
                    }
                } else if (propName.equals(QName.JCR_MIXINTYPES)) {
                    // jcr:mixinTypes
                    mixinTypes = parseNames(attrValue);
                } else if (propName.equals(QName.JCR_UUID)) {
                    // jcr:uuid
                    if (attrValue.length() > 0) {
                        id = NodeId.valueOf(attrValue);
                    }
                } else {
                    props.add(new PropInfo(
                            propName, PropertyType.UNDEFINED, propValues));
                }
            }

            NodeInfo node =
                    new NodeInfo(nodeName, nodeTypeName, mixinTypes, id);
            // all information has been collected, now delegate to importer
            importer.startNode(node, props);
            // push current node data onto stack
            stack.push(node);
        } catch (RepositoryException re) {
            throw new SAXException(re);
        }
    }

    /**
     * Parses the given string as a list of JCR names. Any whitespace sequence
     * is supported as a names separator instead of just a single space to
     * be more liberal in what we accept. The current namespace context is
     * used to convert the prefixed name strings to QNames.
     *
     * @param value string value
     * @return the parsed names
     * @throws SAXException if an invalid name was encountered
     */
    private QName[] parseNames(String value) throws SAXException {
        String[] names = value.split("\\p{Space}+");
        QName[] qnames = new QName[names.length];
        for (int i = 0; i < names.length; i++) {
            try {
                qnames[i] = nsContext.getQName(names[i]);
            } catch (NameException ne) {
                throw new SAXException("Invalid name: " + names[i], ne);
            }
        }
        return qnames;
    }

    /**
     * {@inheritDoc}
     */
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        /**
         * buffer data reported by the characters event;
         * will be processed on the next endElement or startElement event.
         */
        appendCharacters(ch, start, length);
    }

    /**
     * {@inheritDoc}
     */
    public void ignorableWhitespace(char[] ch, int start, int length)
            throws SAXException {
        /**
         * buffer data reported by the ignorableWhitespace event;
         * will be processed on the next endElement or startElement event.
         */
        appendCharacters(ch, start, length);
    }

    /**
     * {@inheritDoc}
     */
    public void endElement(String namespaceURI, String localName, String qName)
            throws SAXException {
        // process buffered character data
        processCharacters();

        NodeInfo node = (NodeInfo) stack.peek();
        try {
            // call Importer
            importer.endNode(node);
        } catch (RepositoryException re) {
            throw new SAXException(re);
        }
        // we're done with this node, pop it from stack
        stack.pop();
    }
}
