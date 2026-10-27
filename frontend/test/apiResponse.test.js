import test from 'node:test';
import assert from 'node:assert/strict';
import { API_MISMATCH_MESSAGE, isGalaxamHealthResponse, isHtmlApiResponse } from '../src/api/apiResponse.js';

test('rejects HTML pages even when the host answers HTTP 200', () => {
    const response = {
        status: 200,
        headers: { 'content-type': 'text/html; charset=utf-8' },
        data: '<!doctype html><html><title>Example Domain</title></html>',
    };

    assert.equal(isHtmlApiResponse(response.data, response.headers['content-type']), true);
    assert.equal(isGalaxamHealthResponse(response), false);
    assert.match(API_MISMATCH_MESSAGE, /API GALAXAM/);
});

test('accepts only the GALAXAM health payload', () => {
    assert.equal(isGalaxamHealthResponse({ status: 200, headers: { 'content-type': 'application/json' }, data: { status: 'UP' } }), true);
    assert.equal(isGalaxamHealthResponse({ status: 200, headers: { 'content-type': 'application/json' }, data: { status: 'DOWN' } }), false);
    assert.equal(isGalaxamHealthResponse({ status: 404, headers: { 'content-type': 'application/json' }, data: { status: 'UP' } }), false);
});

test('does not classify JSON or ordinary text as an HTML fallback', () => {
    assert.equal(isHtmlApiResponse('{"status":"UP"}', 'application/json'), false);
    assert.equal(isHtmlApiResponse('Période introuvable', 'text/plain'), false);
});
