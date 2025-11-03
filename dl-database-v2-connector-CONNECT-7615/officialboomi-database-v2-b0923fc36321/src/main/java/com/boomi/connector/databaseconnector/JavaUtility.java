package com.boomi.connector.databaseconnector;

//import com.apptio.datalink.shared.Constants;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

//import javax.ws.rs.BadRequestException;

public class JavaUtility
{
    /**
     * Log the given exception with a stack trace.
     */
    public static <EXCEPTION_TYPE extends Throwable> EXCEPTION_TYPE log(Logger logger, EXCEPTION_TYPE e)
    {
        if (logger.isErrorEnabled())
        {
            logger.error(e.getLocalizedMessage(), e);
        }
        return e;
    }

    /**
     * Log the given exception message only, no stack trace.
     */
    public static <EXCEPTION_TYPE extends Throwable> EXCEPTION_TYPE logMessage(Logger logger, EXCEPTION_TYPE e)
    {
        if (logger.isErrorEnabled())
        {
            logger.error(e.getLocalizedMessage());
        }
        return e;
    }

   /* public static void validateUUID(String uuid, String resourceName)
    {
        if(StringUtils.isBlank(uuid))
        {
            throw new BadRequestException(resourceName + " is empty");
        }
        if (!uuid.matches(Constants.UUID_REGEX_V1_TO_V5))
        {
            throw new BadRequestException("Invalid " + resourceName);
        }
    }*/
}
