/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.isis.runtimes.dflt.objectstores.sql;

import org.apache.isis.core.metamodel.adapter.ObjectAdapter;
import org.apache.isis.core.metamodel.adapter.oid.Oid;
import org.apache.isis.core.metamodel.spec.ObjectSpecification;
import org.apache.isis.runtimes.dflt.objectstores.sql.SqlOid.State;
import org.apache.isis.runtimes.dflt.objectstores.sql.jdbc.JdbcConnector;
import org.apache.isis.runtimes.dflt.runtime.persistence.oidgenerator.simple.SerialOid;
import org.apache.isis.runtimes.dflt.runtime.system.context.IsisContext;
import org.apache.isis.runtimes.dflt.runtime.system.persistence.AdapterManager;

public class IdMappingAbstract {
    private String column;

    protected void setColumn(final String column) {
        this.column = Sql.identifier(column);
    }

    protected String getColumn() {
        return column;
    }

    public void appendWhereClause(final DatabaseConnector connector, final StringBuffer sql, final Oid oid) {
        sql.append(column);
        sql.append(" = ");
        appendObjectId(connector, sql, oid);
    }

    public void appendObjectId(final DatabaseConnector connector, final StringBuffer sql, final Oid oid) {
        sql.append("?");
        connector.addToQueryValues(primaryKeyAsObject(oid));
    }

    public void appendCreateColumnDefinitions(final StringBuffer sql) {
        sql.append(column);
        sql.append(" ");
        sql.append(JdbcConnector.TYPE_PK() + " NOT NULL PRIMARY KEY");
    }

    public void appendColumnDefinitions(final StringBuffer sql) {
        sql.append(column);
        sql.append(" ");
        sql.append(JdbcConnector.TYPE_PK());
    }

    public void appendColumnNames(final StringBuffer sql) {
        sql.append(column);
    }

    public void appendInsertValues(final DatabaseConnector connector, final StringBuffer sql, final ObjectAdapter object) {
        if (object == null) {
            sql.append("NULL");
        } else {
            appendObjectId(connector, sql, object.getOid());
            // sql.append(connector.addToQueryValues(primaryKeyAsObject(object.getOid())));
        }
    }

    /*
     * This doesn't have to be an Int, it should be any object.
     */
    public Object primaryKeyAsObject(final Oid oid) {
        if (oid instanceof SqlOid) {
            final PrimaryKey pk = ((SqlOid) oid).getPrimaryKey();
            return pk.naturalValue();
        } else {
            return ((SerialOid) oid).getSerialNo();
        }
    }

    public String primaryKey(final Oid oid) {
        if (oid instanceof SqlOid) {
            return "" + ((SqlOid) oid).getPrimaryKey().stringValue() + "";
        } else {
            return "" + ((SerialOid) oid).getSerialNo();
        }
    }

    public Oid recreateOid(final Results rs, final ObjectSpecification specification) {
        PrimaryKey key;
        final Object object = rs.getObject(column);
        if (object == null) {
            return null;
        } else {
            final int id = ((Integer) object).intValue();
            key = new IntegerPrimaryKey(id);
        }
        final Oid oid = new SqlOid(specification.getFullIdentifier(), key, State.PERSISTENT);
        return oid;
    }

    protected ObjectAdapter getAdapter(final ObjectSpecification specification, final Oid oid) {
        final AdapterManager objectLoader = IsisContext.getPersistenceSession().getAdapterManager();
        final ObjectAdapter adapter = objectLoader.getAdapterFor(oid);
        if (adapter != null) {
            return adapter;
        } else {
            return IsisContext.getPersistenceSession().recreateAdapter(oid, specification);
        }
    }

}