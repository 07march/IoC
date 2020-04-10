package com.company;

import org.reflections.Reflections;

import java.lang.reflect.*;
import java.util.*;

//configuration variants
//java base
//xml
//annotation

//inject variants
//setter
//constructor
//field

//Чтение определений компонентов
//Создание экземпляров компонентов
//Внедрение зависимостей в поля и методы установки экземпляров компонентов

public class ClassFactory {
    private Map<String, Object> container = new HashMap<>();
    private Class<?> classConfig;
    private List<Method> componentsDefMethods = new ArrayList<>();
    private List<Class<?>> componentsDefClasses = new ArrayList<>();

    public ClassFactory(Class<?> configuration) throws IllegalAccessException {
        if (configuration.isAnnotationPresent(Configuration.class)) {
            this.classConfig = configuration;
            readDefMethod();
            readDefClass();

            create();
            createInstance();

            injectSetter();
            injectField();
        } else {
            throw new RuntimeException("This class not configuration");
        }
    }

    private void injectField() throws IllegalAccessException {
        for (Object value : container.values()) {
            for (Field declaredField : value.getClass().getDeclaredFields()) {
                if (declaredField.isAnnotationPresent(Value.class)) {
                    String value1 = declaredField.getDeclaredAnnotation(Value.class).value();
                    declaredField.setAccessible(true);
                    declaredField.set(value, value1);
                }
                if (declaredField.isAnnotationPresent(Autowired.class)) {
                    if (declaredField.isAnnotationPresent(Qualifier.class)) {
                        String valueQua = declaredField.getDeclaredAnnotation(Qualifier.class).value();
                        Object o = container.get(valueQua);
                        declaredField.setAccessible(true);
                        declaredField.set(value, o);
                    } else {
                        for (Object o : container.values()) {
                            if (declaredField.getType().equals(o.getClass())) {
                                declaredField.setAccessible(true);
                                declaredField.set(value, o);
                            }
                        }
                    }
                }
            }
        }
    }

    public <T> T getComponent(String componentName, Class<T> tClass) {
        Object o = container.get(componentName);
        return tClass.cast(o);
    }

    public <T> T[] getAllComponents() {
        return (T[]) container.values().toArray(new Object[0]);
    }

    private void readDefMethod() {
        for (Method method : classConfig.getMethods()) {
            if (method.isAnnotationPresent(Component.class)) {
                componentsDefMethods.add(method);
            }
        }
    }

    //List(ArrayList, LinkedList) <E> - impl. collection
    //Set(HashSet, TreeSet) <E> - impl. collection
    //Map(HashMap, TreeMap) <K, V>
    //Queue(Qu, Dequ) <E> - impl. collection

    private void readDefClass() {
        if (classConfig.isAnnotationPresent(ComponentScan.class)) {
            String value = classConfig.getDeclaredAnnotation(ComponentScan.class).value();
            Reflections reflections = new Reflections(value);
            Set<Class<?>> typesAnnotatedWith = reflections.getTypesAnnotatedWith(Component.class);
            componentsDefClasses.addAll(typesAnnotatedWith);
        }
    }

    private void injectSetter() {
        for (Object object : container.values()) {
            for (Method method : object.getClass().getMethods()) {
                if (method.getName().contains("set")) {
                    Parameter parameter = method.getParameters()[0];
                    Class<?> type = parameter.getType();
                    for (Object value : container.values()) {
                        if (value.getClass().equals(type)) {
                            try {
                                method.invoke(object, value);
                            } catch (IllegalAccessException | InvocationTargetException e) {
                                e.printStackTrace();
                            }
                        }
//                        else throw new RuntimeException("fiasko");
                    }
                }
            }
        }
    }

    private void createInstance() {
        for (Class<?> componentsDefClass : componentsDefClasses) {
            if (componentsDefClass.getConstructors().length == 1) {
                Constructor<?> constructor = componentsDefClass.getConstructors()[0];
                List<Object> objects = new ArrayList<>();
                Parameter[] parameters = constructor.getParameters();
                for (Parameter parameter : parameters) {
                    if (parameter.isAnnotationPresent(Value.class)) {
                        String value = parameter.getDeclaredAnnotation(Value.class).value();
                        objects.add(value);
                    } else objects.add(null);
                }
                try {
                    container.put(componentsDefClass.getSimpleName().toLowerCase(), constructor.newInstance(objects.toArray(new Object[0])));
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            } else {
                throw new RuntimeException("constructor length > 1: " + componentsDefClass.getName());
            }
        }
    }

    private void create() {
        Object obj = null;
        try {
            obj = classConfig.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        for (Method method : componentsDefMethods) {
            try {
                List<Object> objects = new ArrayList<>();
                for (Parameter parameter : method.getParameters()) {
                    if (parameter.isAnnotationPresent(Value.class)) {
                        String value = parameter.getDeclaredAnnotation(Value.class).value();
                        objects.add(value);
                    } else objects.add(null);
                }
                Object invoke = method.invoke(obj, objects.toArray(new Object[0]));
                String name = method.getName();
                container.put(name, invoke);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }
}
