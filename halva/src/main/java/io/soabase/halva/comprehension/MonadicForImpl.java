/**
 * Copyright 2016 Jordan Zimmerman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.soabase.halva.comprehension;

import io.soabase.halva.any.Any;
import io.soabase.halva.any.AnyVal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Implementation for inner-type-less MonadicFor
 */
public class MonadicForImpl<M>
{
    public <R> MonadicForImpl(AnyVal<R> any, M startingMonad, MonadicForWrapper<M> wrapper)
    {
        this.wrapper = wrapper;
        entries.add(new Entry<>(any, () -> startingMonad));
    }

    /////////////////////////////////////////////////////////////

    @SuppressWarnings("unchecked")
    public <R> MonadicForImpl<M> forComp(AnyVal<R> any, Supplier<? extends M> monadSupplier)
    {
        entries.add(new Entry(any, monadSupplier));
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> MonadicForImpl<M> letComp(AnyVal<T> any, Supplier<T> letSupplier)
    {
        getPreviousEntry().setters.add(() -> any.set(letSupplier.get()));
        return this;
    }

    public MonadicForImpl<M> filter(Supplier<Boolean> test)
    {
        getPreviousEntry().predicates.add(test);
        return this;
    }

    public <R> M yield(Supplier<R> yieldSupplier)
    {
        return yieldLoopInline(0, yieldSupplier);
    }

    /////////////////////////////////////////////////////////////

    // INTERNALS

    private final MonadicForWrapper<M> wrapper;

    // From ForImpl

    private final List<Entry<M>> entries = new ArrayList<>();

    private static class Entry<M>
    {
        final Any any;
        final Supplier<M> monadSupplier;
        final List<Supplier<Boolean>> predicates = new ArrayList<>();
        final List<Runnable> setters = new ArrayList<>();

        Entry(Any any, Supplier<M> stream)
        {
            this.any = any;
            this.monadSupplier = stream;
        }
    }

    private Entry<M> getPreviousEntry()
    {
        if ( entries.size() == 0 )
        {
            throw new IllegalStateException("No generators to apply to");
        }
        return entries.get(entries.size() - 1);
    }

    @SuppressWarnings("unchecked")
    private M yieldLoopInline(int index, Supplier<?> yielder)
    {
        Entry<M> entry = entries.get(index);
        M stream = wrapper.map(entry.monadSupplier.get(), o -> {
            entry.any.set(o);
            return o;
        });

        if ( entry.predicates.size() != 0 )
        {
            stream = wrapper.filter(stream, o -> entry.predicates.stream().allMatch(Supplier::get));
        }

        if ( entry.setters.size() != 0 )
        {
            stream = wrapper.map(stream, o -> {
                entry.setters.stream().forEach(Runnable::run);
                return o;
            });
        }

        if ( (index + 1) < entries.size() )
        {
            stream = wrapper.flatMap(stream, o -> yieldLoopInline(index + 1, yielder));
        }
        else
        {
            stream = wrapper.map(stream, o -> yielder.get());
        }
        return stream;
    }
}

