package com.psddev.cms.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.psddev.dari.util.CodeUtils;
import com.psddev.dari.util.TypeDefinition;

/**
 * Binds a model with a view model to produce a view.
 *
 * @param <M> the model type to bind with the view model.
 */
public abstract class ViewModel<M> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ViewModel.class);

    private static final LoadingCache<Class<?>, LoadingCache<Object, Optional<Class<?>>>> VIEW_BINDINGS = CacheBuilder.newBuilder()
            .weakKeys()
            .build(new CacheLoader<Class<?>, LoadingCache<Object, Optional<Class<?>>>>() {

                @Override
                @ParametersAreNonnullByDefault
                public LoadingCache<Object, Optional<Class<?>>> load(Class<?> modelClass) {
                    return CacheBuilder.newBuilder()
                            .weakKeys()
                            .build(new CacheLoader<Object, Optional<Class<?>>>() {

                                @Override
                                @ParametersAreNonnullByDefault
                                public Optional<Class<?>> load(final Object viewTypeObject) {

                                    Class<?> viewClass = viewTypeObject instanceof Class ? (Class<?>) viewTypeObject : null;
                                    String viewType = viewTypeObject instanceof String ? (String) viewTypeObject : null;

                                    return Optional.ofNullable(findViewModelClassCacheHelper(viewClass, viewType, modelClass));
                                }
                            });
                }
            });

    static {
        CodeUtils.addRedefineClassesListener(classes -> VIEW_BINDINGS.invalidateAll());
    }

    private ViewModelCreator viewModelCreator;

    private ViewResponse viewResponse;

    protected M model;

    /**
     * Called during creation of this view model. The object is fully initialized
     * at this point so it is safe to utilize full functionality.
     *
     * @param response the current view response.
     */
    protected void onCreate(ViewResponse response) {
        // do nothing by default
    }

    /**
     * Creates a view of type {@code viewClass} that is bound to the given
     * {@code model}.
     *
     * @param viewClass the type of view to create.
     * @param model the model used to create the view.
     * @param <T> the model type.
     * @param <V> the view type.
     * @return a newly created view.
     */
    protected final <T, V> V createView(Class<V> viewClass, T model) {

        Class<? extends ViewModel<? super T>> viewModelClass = findViewModelClassHelper(viewClass, null, model, true);
        if (viewModelClass != null) {

            ViewModel<? super T> viewModel = viewModelCreator.createViewModel(viewModelClass, model, viewResponse);

            if (viewModel != null && viewClass.isAssignableFrom(viewModel.getClass())) {

                @SuppressWarnings("unchecked")
                V view = (V) viewModel;

                return view;
            }
        }

        return null;
    }

    /**
     * Creates a view that is bound to the given {@code viewType} and
     * {@code model}.
     *
     * @param viewType the view type key bound to the view and model.
     * @param model the model used to create the view.
     * @param <T> the model type.
     * @return a newly created view.
     */
    protected final <T> Object createView(String viewType, T model) {

        Class<? extends ViewModel<? super T>> viewModelClass = findViewModelClassHelper(null, viewType, model, true);
        if (viewModelClass != null) {

            return viewModelCreator.createViewModel(viewModelClass, model, viewResponse);
        }

        return null;
    }

    /**
     * The default view model creator.
     */
    public static class DefaultCreator implements ViewModelCreator {

        @Override
        public final <M, VM extends ViewModel<? super M>> VM createViewModel(Class<VM> viewModelClass, M model, ViewResponse viewResponse) {

            if (findViewModelClassHelper(viewModelClass, null, model, false) != null) {

                VM viewModel = TypeDefinition.getInstance(viewModelClass).newInstance();

                ((ViewModel<? super M>) viewModel).viewModelCreator = this;
                ((ViewModel<? super M>) viewModel).viewResponse = viewResponse;

                viewModel.model = model;

                beforeViewModelOnCreate(viewModel);

                viewModel.onCreate(viewResponse);

                return viewModel;
            }

            return null;
        }

        /**
         * Called immediately before {@link ViewModel#onCreate(ViewResponse)}
         * is invoked. Sub-classes may override this method to further
         * initialize the {@link ViewModel}.
         *
         * @param viewModel the viewModel to modify.
         * @param <M> the model type for the ViewModel.
         * @param <VM> the ViewModel type.
         */
        protected <M, VM extends ViewModel<? super M>> void beforeViewModelOnCreate(VM viewModel) {
            // do nothing by default
        }
    }

    /**
     * Finds an appropriate ViewModel class based on the given view class, and
     * model. If more than one class is found, the result is ambiguous and null
     * is returned.
     *
     * @param viewClass the desired compatible class of the returned view model.
     * @param model the model used to look up available view model classes, that is also compatible with the returned view model class.
     * @param <M> the model type
     * @param <V> the view type
     * @return the view model class that matches the bounds of the arguments.
     */
    public static <M, V> Class<? extends ViewModel<? super M>> findViewModelClass(Class<V> viewClass, M model) {
        return findViewModelClassHelper(viewClass, null, model, false);
    }

    /**
     * Finds an appropriate ViewModel class based on the given view type, and
     * model. If more than one class is found, the result is ambiguous and null
     * is returned.
     *
     * @param viewType the desired view type that is bound to the returned view model.
     * @param model the model used to look up available view model classes, that is also compatible with the returned view model class.
     * @param <M> the model type
     * @return the view model class that matches the bounds of the arguments.
     */
    public static <M> Class<? extends ViewModel<? super M>> findViewModelClass(String viewType, M model) {
        return findViewModelClassHelper(null, viewType, model, false);
    }

    private static <M, V> Class<? extends ViewModel<? super M>> findViewModelClassHelper(Class<V> viewClass, String viewType, M model, boolean logFailure) {

        if (model == null) {
            return null;
        }

        Class<? extends ViewModel<? super M>> viewModelClass = findViewModelClassHelper(viewClass, viewType, model);

        if (viewModelClass == null && logFailure) {
            String message = String.format("Could not find ViewModel class for model of type [%s] and view of type [%s].",
                    model.getClass().getName(),
                    Stream.of(viewClass != null ? viewClass.getName() : null, viewType).filter(Objects::nonNull).collect(Collectors.joining(" and")));

            LOGGER.warn(message, new IllegalArgumentException());
        }

        return viewModelClass;
    }

    private static <M, V> Class<? extends ViewModel<? super M>> findViewModelClassHelper(Class<V> viewClass, String viewType, M model) {

        if (model == null) {
            return null;
        }

        try {
            LoadingCache<Object, Optional<Class<?>>> viewTypes = VIEW_BINDINGS.get(model.getClass());

            if (viewTypes != null) {
                Object viewTypeObject = null;

                if (viewClass != null) {
                    viewTypeObject = viewClass;

                } else if (viewType != null) {
                    viewTypeObject = viewType;
                }

                if (viewTypeObject != null) {
                    Optional<Class<?>> viewModelClass = viewTypes.get(viewTypeObject);

                    if (viewModelClass.isPresent()) {
                        return (Class<? extends ViewModel<? super M>>) viewModelClass.get();
                    }
                }
            }

        } catch (ExecutionException e) {
            // ignore
        }

        return null;
    }

    private static Class<? extends ViewModel<?>> findViewModelClassCacheHelper(Class<?> viewClass, String viewType, Class<?> modelClass) {

        if (modelClass == null) {
            return null;
        }

        // if it's a view model class, with no type specified, then just verify that the model types match.
        if (viewClass != null && viewType == null
                && ViewModel.class.isAssignableFrom(viewClass)) {

            Class<?> declaredModelClass = TypeDefinition.getInstance(viewClass).getInferredGenericTypeArgumentClass(ViewModel.class, 0);

            if (declaredModelClass != null && declaredModelClass.isAssignableFrom(modelClass)) {

                @SuppressWarnings("unchecked")
                Class<? extends ViewModel<?>> viewModelClass = (Class<? extends ViewModel<?>>) viewClass;

                return viewModelClass;

            } else {
                return null;
            }

        } else { // do a lookup of the view bindings on the model.

            Map<Class<?>, List<Class<? extends ViewModel>>> modelToViewModelClassMap = new HashMap<>();

            Set<Class<? extends ViewModel>> allViewModelClasses = new LinkedHashSet<>();

            for (Class<?> annotatableClass : ViewUtils.getAnnotatableClasses(modelClass)) {

                allViewModelClasses.addAll(Arrays.stream(annotatableClass.getAnnotationsByType(ViewBinding.class))

                        // check that it matches the view type if it exists
                        .filter(viewBinding -> viewType == null || Arrays.asList(viewBinding.types()).contains(viewType))

                        // get the annotation's view model class
                        .map(ViewBinding::value)

                        .collect(Collectors.toList()));
            }

            allViewModelClasses.forEach(viewModelClass -> {

                TypeDefinition<? extends ViewModel> typeDef = TypeDefinition.getInstance(viewModelClass);

                Class<?> declaredModelClass = typeDef.getInferredGenericTypeArgumentClass(ViewModel.class, 0);

                if (declaredModelClass != null && declaredModelClass.isAssignableFrom(modelClass)
                        && (viewClass == null || viewClass.isAssignableFrom(viewModelClass))) {

                    List<Class<? extends ViewModel>> viewModelClasses = modelToViewModelClassMap.get(declaredModelClass);
                    if (viewModelClasses == null) {
                        viewModelClasses = new ArrayList<>();
                        modelToViewModelClassMap.put(declaredModelClass, viewModelClasses);
                    }
                    viewModelClasses.add(viewModelClass);
                }
            });

            if (!modelToViewModelClassMap.isEmpty()) {

                Set<Class<?>> nearestModelClasses = ViewUtils.getNearestSuperClassesInSet(modelClass, modelToViewModelClassMap.keySet());
                if (nearestModelClasses.size() == 1) {

                    List<Class<? extends ViewModel>> viewModelClasses = modelToViewModelClassMap.get(nearestModelClasses.iterator().next());
                    if (viewModelClasses.size() == 1) {
                        @SuppressWarnings("unchecked")
                        Class<? extends ViewModel<?>> viewModelClass = (Class<? extends ViewModel<?>>) viewModelClasses.get(0);

                        return viewModelClass;
                    } else {
                        LOGGER.warn("Found [{}] conflicting view model bindings for model type [{}] and view type [{}]: [{}]",
                                new Object[] {
                                        viewModelClasses.size(),
                                        modelClass,
                                        viewClass != null ? viewClass.getName() : null,
                                        viewModelClasses.stream().map(Class::getName).collect(Collectors.joining(", "))
                                });
                    }
                } else {
                    Set<Class<? extends ViewModel>> conflictingViewModelClasses = new LinkedHashSet<>();
                    for (Class<?> nearestModelClass : nearestModelClasses) {
                        conflictingViewModelClasses.addAll(modelToViewModelClassMap.get(nearestModelClass));
                    }
                    LOGGER.warn("Found [{}] conflicting view model bindings for model type [{}] and view type [{}]: [{}]",
                            new Object[] {
                                    conflictingViewModelClasses.size(),
                                    modelClass.getName(),
                                    viewClass != null ? viewClass.getName() : null,
                                    conflictingViewModelClasses.stream().map(Class::getName).collect(Collectors.joining(", "))
                            });
                }
            }
        }

        return null;
    }

    /**
     * @deprecated Use {@link #findViewModelClass(Class, Object)} or {@link #findViewModelClass(String, Object)} instead.
     */
    @Deprecated
    public static <M, V> Class<? extends ViewModel<? super M>> findViewModelClass(Class<V> viewClass, String viewType, M model) {
        return findViewModelClassHelper(viewClass, viewType, model, false);
    }
}
