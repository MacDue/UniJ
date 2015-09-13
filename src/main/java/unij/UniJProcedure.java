package unij;

import java.lang.reflect.Method;

class UniJProcedure {
	final Object executor;
	final Method callback;
	final Class[] paramTypes;
	final boolean willReturnSomething;

	protected UniJProcedure(Object executor, Method callback) {
		this.executor = executor;
		this.callback = callback;
		this.paramTypes = callback.getParameterTypes();
		this.willReturnSomething = callback.getReturnType() != Void.TYPE;
	}

	protected Object execute(Object... parameters) {
		try {
			return callback.invoke(executor, parameters);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "Could not execute";
	}
}
