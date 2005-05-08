/*
 * Copyright 2004-2005 The Apache Software Foundation or its licensors,
 *                     as applicable.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.base;

import javax.jcr.AccessDeniedException;
import javax.jcr.InvalidItemStateException;
import javax.jcr.Item;
import javax.jcr.ItemNotFoundException;
import javax.jcr.ItemVisitor;
import javax.jcr.Node;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.VersionException;

/**
 * Item base class.
 */
public class BaseItem implements Item {

    /** Protected constructor. This class is only useful when extended. */
    protected BaseItem() {
    }

    /**
     * Implemented by calling <code>getParent().getPath()</code> and
     * appending <code>getName()</code> to the returned parent path.
     * Returns the root path <code>/</code> if an
     * {@link ItemNotFoundException ItemNotFoundException} is thrown by
     * <code>getParent()</code> (indicating that this is the root node).
     * {@inheritDoc}
     */
    public String getPath() throws RepositoryException {
        try {
            String path = getParent().getPath();
            if (path.equals("/")) {
                return path + getName();
            } else {
                return path + "/" + getName();
            }
        } catch (ItemNotFoundException e) {
            return "/";
        }
    }

    /** Not implemented. {@inheritDoc} */
    public String getName() throws RepositoryException {
        throw new UnsupportedRepositoryOperationException();
    }

    /**
     * Implemented by calling <code>getDepth()</code> and returning either
     * this item, <code>getSession().getRootNode()</code>, or
     * <code>getParent().getAncestor()</code>, or throwing an
     * {@link ItemNotFoundException ItemNotFoundException} depending on the
     * given depth.
     * {@inheritDoc}
     */
    public Item getAncestor(int depth) throws ItemNotFoundException,
            AccessDeniedException, RepositoryException {
        int thisDepth = getDepth();
        if (thisDepth == depth) {
            return this;
        } else if (depth == 0) {
            return getSession().getRootNode();
        } else if (depth > 0 && depth < thisDepth) {
            return getParent().getAncestor(depth);
        } else {
            throw new ItemNotFoundException("Invalid ancestor depth " + depth);
        }
    }

    /** Not implemented. {@inheritDoc} */
    public Node getParent() throws ItemNotFoundException,
            AccessDeniedException, RepositoryException {
        throw new UnsupportedRepositoryOperationException();
    }

    /**
     * Implemented by calling <code>getParent().getDepth() + 1</code> and
     * returning <code>0</code> if an
     * {@link ItemNotFoundException ItemNotFoundException} is thrown by
     * <code>getParent()</code> (indicating that this is the root node).
     * {@inheritDoc}
     */
    public int getDepth() throws RepositoryException {
        try {
            return getParent().getDepth() + 1;
        } catch (ItemNotFoundException e) {
            return 0;
        }
    }

    /** Not implemented. {@inheritDoc} */
    public Session getSession() throws RepositoryException {
        throw new UnsupportedRepositoryOperationException();
    }

    /** Always returns <code>false</code>. {@inheritDoc} */
    public boolean isNode() {
        return false;
    }

    /** Not implemented. {@inheritDoc} */
    public boolean isNew() {
        throw new UnsupportedOperationException();
    }

    /** Not implemented. {@inheritDoc} */
    public boolean isModified() {
        throw new UnsupportedOperationException();
    }

    /** Not implemented. {@inheritDoc} */
    public boolean isSame(Item otherItem) {
        throw new UnsupportedOperationException();
    }

    /** Does nothing. {@inheritDoc} */
    public void accept(ItemVisitor visitor) throws RepositoryException {
    }

    /** Not implemented. {@inheritDoc} */
    public void save() throws AccessDeniedException,
            ConstraintViolationException, InvalidItemStateException,
            ReferentialIntegrityException, VersionException, LockException,
            RepositoryException {
        throw new UnsupportedRepositoryOperationException();
    }

    /** Not implemented. {@inheritDoc} */
    public void refresh(boolean keepChanges) throws InvalidItemStateException,
            RepositoryException {
        throw new UnsupportedRepositoryOperationException();
    }

    /** Not implemented. {@inheritDoc} */
    public void remove() throws VersionException, LockException,
            RepositoryException {
        throw new UnsupportedRepositoryOperationException();
    }

}
