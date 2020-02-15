package dev.openrs2.deob.ast.util

import com.github.javaparser.ast.expr.BinaryExpr
import com.github.javaparser.ast.expr.BooleanLiteralExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.IntegerLiteralExpr
import com.github.javaparser.ast.expr.LongLiteralExpr
import com.github.javaparser.ast.expr.UnaryExpr

fun IntegerLiteralExpr.checkedAsInt(): Int {
    val n = asNumber()
    if (n !is Int) {
        error("Invalid IntegerLiteralExpr type")
    }
    return n
}

fun LongLiteralExpr.checkedAsLong(): Long {
    val n = asNumber()
    if (n !is Long) {
        error("Invalid LongLiteralExpr type")
    }
    return n
}

fun Expression.isIntegerOrLongLiteral(): Boolean {
    return isIntegerLiteralExpr || isLongLiteralExpr
}

fun createLong(value: Long): LongLiteralExpr {
    return LongLiteralExpr(value.toString() + "L")
}

fun Expression.negate(): Expression {
    return if (isUnaryExpr && asUnaryExpr().operator == UnaryExpr.Operator.MINUS) {
        asUnaryExpr().expression.clone()
    } else if (isIntegerLiteralExpr) {
        when (val n = asIntegerLiteralExpr().asNumber()) {
            IntegerLiteralExpr.MAX_31_BIT_UNSIGNED_VALUE_AS_LONG -> IntegerLiteralExpr(Integer.MIN_VALUE.toString())
            is Int -> IntegerLiteralExpr((-n.toInt()).toString())
            else -> error("Invalid IntegerLiteralExpr type")
        }
    } else if (isLongLiteralExpr) {
        when (val n = asLongLiteralExpr().asNumber()) {
            LongLiteralExpr.MAX_63_BIT_UNSIGNED_VALUE_AS_BIG_INTEGER -> createLong(Long.MIN_VALUE)
            is Long -> createLong(-n)
            else -> error("Invalid LongLiteralExpr type")
        }
    } else {
        throw IllegalArgumentException()
    }
}

fun Expression.not(): Expression {
    if (isUnaryExpr) {
        val unary = asUnaryExpr()
        if (unary.operator == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {
            return unary.expression.clone()
        }
    } else if (isBinaryExpr) {
        val binary = asBinaryExpr()

        val left = binary.left
        val right = binary.right

        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (binary.operator) {
            BinaryExpr.Operator.EQUALS ->
                return BinaryExpr(left.clone(), right.clone(), BinaryExpr.Operator.NOT_EQUALS)
            BinaryExpr.Operator.NOT_EQUALS ->
                return BinaryExpr(left.clone(), right.clone(), BinaryExpr.Operator.EQUALS)
            BinaryExpr.Operator.GREATER ->
                return BinaryExpr(left.clone(), right.clone(), BinaryExpr.Operator.LESS_EQUALS)
            BinaryExpr.Operator.GREATER_EQUALS ->
                return BinaryExpr(left.clone(), right.clone(), BinaryExpr.Operator.LESS)
            BinaryExpr.Operator.LESS ->
                return BinaryExpr(left.clone(), right.clone(), BinaryExpr.Operator.GREATER_EQUALS)
            BinaryExpr.Operator.LESS_EQUALS ->
                return BinaryExpr(left.clone(), right.clone(), BinaryExpr.Operator.GREATER)
            BinaryExpr.Operator.AND ->
                return BinaryExpr(left.not(), right.not(), BinaryExpr.Operator.OR)
            BinaryExpr.Operator.OR ->
                return BinaryExpr(left.not(), right.not(), BinaryExpr.Operator.AND)
        }
    } else if (isBooleanLiteralExpr) {
        return BooleanLiteralExpr(!asBooleanLiteralExpr().value)
    }
    return UnaryExpr(clone(), UnaryExpr.Operator.LOGICAL_COMPLEMENT)
}

fun Expression.countNots(): Int {
    var count = 0

    if (isUnaryExpr && asUnaryExpr().operator == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {
        count++
    } else if (isBinaryExpr && asBinaryExpr().operator == BinaryExpr.Operator.NOT_EQUALS) {
        count++
    }

    for (child in findAll(Expression::class.java)) {
        if (child !== this) {
            count += child.countNots()
        }
    }

    return count
}

fun Expression.hasSideEffects(): Boolean {
    if (isLiteralExpr || isNameExpr || isFieldAccessExpr) {
        return false
    } else if (isUnaryExpr) {
        return asUnaryExpr().expression.hasSideEffects()
    } else if (isBinaryExpr) {
        val binary = asBinaryExpr()
        return binary.left.hasSideEffects() || binary.right.hasSideEffects()
    } else if (isArrayAccessExpr) {
        val access = asArrayAccessExpr()
        return access.name.hasSideEffects() || access.index.hasSideEffects()
    }
    // TODO(gpe): more cases
    return true
}
