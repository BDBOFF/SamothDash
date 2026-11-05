#!/usr/bin/env python3
"""Petit client HA pour le test : token, lecture d'état, bascule d'un input_boolean."""
import json, sys, urllib.request as u, urllib.parse as p
LOCAL = 'http://127.0.0.1:8123'
APP = 'http://10.0.2.2:8123'  # adresse vue depuis l'émulateur : c'est le client_id de l'appli
def post(url, data, headers):
    return json.load(u.urlopen(u.Request(LOCAL + url, data, headers)))
def login():
    c = APP + '/'
    j = {'Content-Type': 'application/json'}
    f = post('/auth/login_flow', json.dumps({'client_id': c, 'handler': ['homeassistant', None], 'redirect_uri': APP + '/?auth_callback=1'}).encode(), j)
    f = post('/auth/login_flow/' + f['flow_id'], json.dumps({'client_id': c, 'username': 'samoth', 'password': 'motdepasse'}).encode(), j)
    return post('/auth/token', p.urlencode({'grant_type': 'authorization_code', 'code': f['result'], 'client_id': c}).encode(), {})
cmd = sys.argv[1]
if cmd == 'token':
    print(login()['refresh_token'])
else:
    t = login()['access_token']; h = {'Authorization': 'Bearer ' + t, 'Content-Type': 'application/json'}
    if cmd == 'states':
        for s in json.load(u.urlopen(u.Request(LOCAL + '/api/states', headers=h))):
            if s['entity_id'].startswith(('sensor.tablette', 'binary_sensor.tablette', 'camera.', 'binary_sensor.demo_camera')):
                print(s['entity_id'], s['state'])
    else:
        post('/api/services/input_boolean/turn_' + cmd, json.dumps({'entity_id': 'input_boolean.personne'}).encode(), h)
