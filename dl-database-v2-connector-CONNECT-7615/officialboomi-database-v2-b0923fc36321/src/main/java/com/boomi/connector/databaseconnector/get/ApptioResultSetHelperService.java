package com.boomi.connector.databaseconnector.get;

import com.opencsv.ResultSetHelperService;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

public class ApptioResultSetHelperService extends ResultSetHelperService
{
    public String[] getColumnValues(ResultSet resultSet) throws SQLException, IOException
    {
        String[] toRet = super.getColumnValues(resultSet);

        ResultSetMetaData metadata = resultSet.getMetaData();

        for (int columnId = 1; columnId <= metadata.getColumnCount(); columnId++) {
            if (metadata.getColumnType(columnId) == Types.OTHER)
            {
                Object value = resultSet.getObject(columnId);
                toRet[columnId-1] = value.toString();
            }
        }

        return toRet;
    }
}
