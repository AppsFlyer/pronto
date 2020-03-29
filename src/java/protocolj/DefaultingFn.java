package pronto;

import clojure.lang.IFn;

public interface DefaultingFn extends IFn {

    default Object call() {
        return this.invoke();
    }

    default void run() {
        this.invoke();
    }

    default Object invoke() {
        return Utils.throwArity(this,0);
    }

    default Object invoke(Object arg1) {
        return Utils.throwArity(this,1);
    }

    default Object invoke(Object arg1, Object arg2) {
        return Utils.throwArity(this,2);
    }

    default Object invoke(Object arg1, Object arg2, Object arg3) {
        return Utils.throwArity(this,3);
    }

    default Object invoke(Object arg1, Object arg2, Object arg3, Object arg4) {
        return Utils.throwArity(this,4);
    }

    default Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        return Utils.throwArity(this,5);
    }

    default Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
        return Utils.throwArity(this,6);
    }

    default Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7) {
        return Utils.throwArity(this,7);
    }

    default Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8) {
        return Utils.throwArity(this,8);
    }

    default Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9) {
        return Utils.throwArity(this,9);
    }

    default Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10) {
        return Utils.throwArity(this,10);
    }

    default Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11) {
        return Utils.throwArity(this,11);
    }

    default Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12) {
        return Utils.throwArity(this,12);
    }

    default Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13) {
        return Utils.throwArity(this,13);
    }

    default Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14) {
        return Utils.throwArity(this,14);
    }

    default Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15) {
        return Utils.throwArity(this,15);
    }

    default Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16) {
        return Utils.throwArity(this,16);
    }

    default Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16, Object arg17) {
        return Utils.throwArity(this,17);
    }

    default Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16, Object arg17, Object arg18) {
        return Utils.throwArity(this,18);
    }

    default Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16, Object arg17, Object arg18, Object arg19) {
        return Utils.throwArity(this,19);
    }

    default Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16, Object arg17, Object arg18, Object arg19, Object arg20) {
        return Utils.throwArity(this,20);
    }

    default Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16, Object arg17, Object arg18, Object arg19, Object arg20, Object... args) {
        return Utils.throwArity(this,21);
    }

}
