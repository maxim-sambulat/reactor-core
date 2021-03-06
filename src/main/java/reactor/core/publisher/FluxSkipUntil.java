/*
 * Copyright (c) 2011-2016 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package reactor.core.publisher;

import java.util.Objects;
import java.util.function.Predicate;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.Fuseable.ConditionalSubscriber;
import reactor.core.Loopback;
import reactor.core.Producer;
import reactor.core.Receiver;
import reactor.core.Trackable;
import reactor.core.Exceptions;

/**
 * Skips source values while a predicate returns
 * true for the value.
 *
 * @param <T> the value type
 */

/**
 * @see <a href="https://github.com/reactor/reactive-streams-commons">Reactive-Streams-Commons</a>
 */
final class FluxSkipUntil<T> extends FluxSource<T, T> {

	final Predicate<? super T> predicate;

	public FluxSkipUntil(Publisher<? extends T> source, Predicate<? super T> predicate) {
		super(source);
		this.predicate = Objects.requireNonNull(predicate, "predicate");
	}

	public Publisher<? extends T> source() {
		return source;
	}

	public Predicate<? super T> predicate() {
		return predicate;
	}

	@Override
	public void subscribe(Subscriber<? super T> s) {
		source.subscribe(new SkipWhileSubscriber<>(s, predicate));
	}

	static final class SkipWhileSubscriber<T>
			implements ConditionalSubscriber<T>, Receiver, Producer, Loopback,
			           Subscription, Trackable {
		final Subscriber<? super T> actual;

		final Predicate<? super T> predicate;

		Subscription s;

		boolean done;

		boolean skipped;

		public SkipWhileSubscriber(Subscriber<? super T> actual, Predicate<? super T> predicate) {
			this.actual = actual;
			this.predicate = predicate;
		}

		@Override
		public void onSubscribe(Subscription s) {
			if (Operators.validate(this.s, s)) {
				this.s = s;
				actual.onSubscribe(this);
			}
		}

		@Override
		public void onNext(T t) {
			if (done) {
				Exceptions.onNextDropped(t);
				return;
			}

			if (skipped){
				actual.onNext(t);
				return;
			}
			boolean b;

			try {
				b = predicate.test(t);
			} catch (Throwable e) {
				onError(Exceptions.mapOperatorError(s, e, t));
				return;
			}

			if (b) {
				skipped = true;
				actual.onNext(t);
				return;
			}

			s.request(1);
		}

		@Override
		public boolean tryOnNext(T t) {
			if (done) {
				Exceptions.onNextDropped(t);
				return true;
			}

			if (skipped) {
				actual.onNext(t);
				return true;
			}
			boolean b;

			try {
				b = predicate.test(t);
			}
			catch (Throwable e) {
				onError(Exceptions.mapOperatorError(s, e, t));

				return true;
			}

			if (b) {
				return false;
			}

			skipped = true;
			actual.onNext(t);
			return true;
		}

		@Override
		public void onError(Throwable t) {
			if (done) {
				Exceptions.onErrorDropped(t);
				return;
			}
			done = true;

			actual.onError(t);
		}

		@Override
		public void onComplete() {
			if (done) {
				return;
			}
			done = true;

			actual.onComplete();
		}

		@Override
		public boolean isStarted() {
			return s != null && !done;
		}

		@Override
		public boolean isTerminated() {
			return done;
		}

		@Override
		public Object downstream() {
			return actual;
		}

		@Override
		public Object connectedInput() {
			return predicate;
		}

		@Override
		public Object upstream() {
			return s;
		}
		
		@Override
		public void request(long n) {
			s.request(n);
		}
		
		@Override
		public void cancel() {
			s.cancel();
		}
	}

}
