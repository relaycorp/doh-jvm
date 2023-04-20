package tech.relaycorp.doh

public class InvalidQueryException(message: String, cause: Throwable) : DoHException(message, cause)
