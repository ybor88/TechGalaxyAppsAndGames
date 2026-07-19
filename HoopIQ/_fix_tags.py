with open(r'c:\TechGalaxyAppsAndGames\HoopIQ\hoopiq_app.py', 'r', encoding='utf-8') as f:
    src = f.read()

OLD = (
    '        self.tree.tag_configure("gold",          background="#1a1500", foreground="#f5c518")\n'
    '        self.tree.tag_configure("silver",        background="#141414", foreground="#c0c0c0")\n'
    '        self.tree.tag_configure("bronze",        background="#120800", foreground="#cd7f32")\n'
    '        self.tree.tag_configure("stato_attivo",  background=BG_CARD,   foreground="#4caf50")\n'
    '        self.tree.tag_configure("stato_inattivo",background="#1a1a1a", foreground="#888888")\n'
    '        self.tree.tag_configure("stato_infort",  background="#1a1000", foreground="#ff9800")\n'
    '        self.tree.tag_configure("stato_ritirato",background="#0d0d1a", foreground="#5c6bc0")\n'
    '        self.tree.tag_configure("incompleto",    background="#1a0d00", foreground="#ff9800")'
)

NEW = (
    '        self.tree.tag_configure("gold",          background="#2a2000", foreground="#f5c518")\n'
    '        self.tree.tag_configure("silver",        background="#252525", foreground="#d8d8d8")\n'
    '        self.tree.tag_configure("bronze",        background="#221200", foreground="#e08c40")\n'
    '        self.tree.tag_configure("stato_attivo",  background="#162416", foreground="#66dd66")\n'
    '        self.tree.tag_configure("stato_inattivo",background="#1e1e1e", foreground="#aaaaaa")\n'
    '        self.tree.tag_configure("stato_infort",  background="#251a00", foreground="#ffb347")\n'
    '        self.tree.tag_configure("stato_ritirato",background="#131326", foreground="#8899ee")\n'
    '        self.tree.tag_configure("incompleto",    background="#281800", foreground="#ffb347")'
)

count = src.count(OLD)
print('occurrences:', count)
if count >= 1:
    out = src.replace(OLD, NEW)
    with open(r'c:\TechGalaxyAppsAndGames\HoopIQ\hoopiq_app.py', 'w', encoding='utf-8') as f:
        f.write(out)
    print('done -', count, 'replaced')
else:
    for i, line in enumerate(src.splitlines()):
        if 'tag_configure' in line and 'gold' in line:
            print(i, repr(line))
