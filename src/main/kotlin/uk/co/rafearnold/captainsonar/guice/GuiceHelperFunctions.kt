package uk.co.rafearnold.captainsonar.guice

import com.google.inject.TypeLiteral

inline fun <reified T> typeLiteral(): TypeLiteral<T> = object : TypeLiteral<T>() {}
