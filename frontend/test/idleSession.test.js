import test from 'node:test';
import assert from 'node:assert/strict';
import { IDLE_TIMEOUT_MS, isIdleExpired, USER_ACTIVITY_EVENTS } from '../src/context/idleSession.js';

test('15 minutes are measured from the last user activity timestamp, including the boundary', () => {
  const lastActivity = 1_000_000;
  assert.equal(IDLE_TIMEOUT_MS, 900_000);
  assert.equal(isIdleExpired(lastActivity, lastActivity + 899_999), false);
  assert.equal(isIdleExpired(lastActivity, lastActivity + 900_000), true);
});

test('refresh, focus, pointer movement and network events are not user activity', () => {
  for (const event of ['focus', 'visibilitychange', 'mousemove', 'pointermove', 'online', 'fetch', 'refresh']) {
    assert.equal(USER_ACTIVITY_EVENTS.includes(event), false, `${event} must not extend the idle deadline`);
  }
  for (const event of ['pointerdown', 'keydown', 'touchstart', 'click', 'input', 'change', 'submit']) {
    assert.equal(USER_ACTIVITY_EVENTS.includes(event), true, `${event} is an explicit user interaction`);
  }
});
