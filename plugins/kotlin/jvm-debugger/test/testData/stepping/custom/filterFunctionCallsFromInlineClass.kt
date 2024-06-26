// ENABLED_LANGUAGE_FEATURE: GenericInlineClassParameter
package filterFunctionCallsFromInlineClass

@JvmInline
value class A<T>(val str: T) {
    fun foo(): A<T> {
        return this
    }

    private fun foo(i: Int?, j: Int): A<T> {
        return this
    }

    fun bar(): A<T> {
        return this
    }

    private fun baz(a: A<*>): B.C {
        return B.C()
    }

    inline fun inlineFoo(): A<T> {
        return this
    }

    private inline fun inlineFoo(i: Int?, j: Int): A<T> {
        return this
    }

    inline fun inlineBar(): A<T> {
        return this
    }

    private inline fun inlineBaz(): A<T> {
        return this
    }

    fun testNoInlineCalls() {
        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 1
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 3
        foo().bar().foo(1, 2).baz(this)

        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
        foo().bar().foo(1, 2).baz(this)

        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 3
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
        foo().bar().foo(1, 2).baz(this)
    }

    fun testFirstCallIsInline() {
        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 1
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 3
        inlineFoo().bar().foo(1, 2).baz(this)

        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
        inlineFoo().bar().foo(1, 2).baz(this)

        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 3
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
        inlineFoo().bar().foo(1, 2).baz(this)
    }

    fun testFirstAndThirdCallsAreInline() {
        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 1
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 3
        inlineFoo().bar().inlineFoo(1, 2).baz(this)

        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
        inlineFoo().bar().inlineFoo(1, 2).baz(this)

        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 3
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
        inlineFoo().bar().inlineFoo(1, 2).baz(this)
    }

    fun testAllCallsAreInline() {
        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 1
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 3
        inlineFoo().inlineBar().inlineFoo(1, 2).inlineBaz()

        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
        inlineFoo().inlineBar().inlineFoo(1, 2).inlineBaz()

        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 3
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
        inlineFoo().inlineBar().inlineFoo(1, 2).inlineBaz()
    }
}

fun Int.foo(a: A<*>): Int {
    return 1
}

fun Int.bar(a: A<*>): Int {
    return 2
}

fun testMethodContainsInlineClassInValueArguments() {
    val a = A("TEXT")

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
    1.foo(a).bar(a).foo(a)

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    1.foo(a).bar(a).foo(a)
}

fun main() {
    val a = A("TEXT")
    a.testNoInlineCalls()
    a.testFirstCallIsInline()
    a.testFirstAndThirdCallsAreInline()
    a.testAllCallsAreInline()
    testMethodContainsInlineClassInValueArguments()
}

fun stopHere() {
}

class B {
    class C
}

// IGNORE_FOR_K2_CODE
// Remove ignore after KT-57227 fix
