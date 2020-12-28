package tech.relaycorp.doh

public abstract class DoHException(message: String, cause: Throwable?) : Exception(message, cause)

public class InvalidQueryException(message: String, cause: Throwable) : DoHException(message, cause)

/**
 * The DoH failed to complete the lookup successfully.
 */
public class LookupFailureException(message: String, cause: Throwable? = null) :
    DoHException(message, cause)
