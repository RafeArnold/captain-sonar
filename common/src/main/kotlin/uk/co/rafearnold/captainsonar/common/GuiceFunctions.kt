package uk.co.rafearnold.captainsonar.common

import com.google.inject.TypeLiteral

inline fun <reified T> typeLiteral(): TypeLiteral<T> = object : TypeLiteral<T>() {}
