#!/usr/bin/env python3
"""Genera un contenido de la academia (manifiesto v2) a partir del curso.json de un curso.

Prueba de importación de la fase 1 del curso inicial (8 de noviembre de 2026). Es el primer
borrador de lo que hará el importador nuevo: leer curso.json y no inventar identificadores.

Uso:
    python3 tools/curso_a_manifiesto.py --curso DIR --commit SHA --salida DIR [--modo semilla|completo]
                                        [--solo-disponibles]

--curso   copia del curso en el commit fijado (sacada con git archive, nunca la carpeta de trabajo).
--commit  hash completo del commit de esa copia; va a source.versionRef.
--salida  raíz de contenido nueva (no debe existir); se crea <salida>/<id>/manifest.json.
--modo    semilla: todos los módulos pendientes, sin cuerpo ni ejercicios, para que content-import
          los rellene desde el commit. completo: los módulos disponibles con su cuerpo copiado y sus
          ejercicios con los identificadores de curso.json.
--solo-disponibles  solo las fases y módulos disponibles (el manifiesto no admite fases sin módulos).

Adaptaciones provisionales a la academia heredada, que anota la prueba:
- Los identificadores de FORMATO.md son «<curso>.<nombre>»; la academia exige «<curso>-…». Se traduce
  el primer punto por un guion: inicial.que-es-programar -> inicial-que-es-programar.
- Los retos, el glosario, el proyecto de tipo «modulo», las partes y los datos de cabecera no tienen
  sitio en el manifiesto v2: no se declaran.
"""

import argparse
import hashlib
import json
import sys
from pathlib import Path


def id_academia(id_curso: str, identificador: str) -> str:
    prefijo = id_curso + "."
    if not identificador.startswith(prefijo):
        raise SystemExit(f"identificador fuera del curso: {identificador}")
    return id_curso + "-" + identificador[len(prefijo):]


def sha256(datos: bytes) -> str:
    return hashlib.sha256(datos).hexdigest()


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--curso", required=True, type=Path)
    p.add_argument("--commit", required=True)
    p.add_argument("--salida", required=True, type=Path)
    p.add_argument("--modo", choices=["semilla", "completo"], default="completo")
    p.add_argument("--solo-disponibles", action="store_true")
    a = p.parse_args()

    if len(a.commit) != 40:
        raise SystemExit("--commit debe ser el hash completo de 40 caracteres")
    if a.salida.exists():
        raise SystemExit(f"--salida ya existe: {a.salida}")

    curso = json.loads((a.curso / "curso.json").read_text(encoding="utf-8"))
    cid = curso["id"]
    raiz = a.salida / cid

    fases, lecciones, enlaces, copias = [], [], [], []
    enlaces.append({"sourcePath": "00-indice.md", "courseId": cid})
    posicion = 0
    for fase in curso["fases"]:
        modulos = [m for m in fase["modulos"] if m.get("disponible", True) or not a.solo_disponibles]
        if not modulos:
            continue
        fase_id = id_academia(cid, fase["id"])
        fases.append({"id": fase_id, "title": fase["titulo"], "position": len(fases) + 1})
        for m in modulos:
            posicion += 1
            disponible = m.get("disponible", True)
            lid = id_academia(cid, m["id"])
            leccion = {
                "id": lid, "title": m["titulo"], "position": posicion, "phaseId": fase_id,
                "sourcePath": m["fichero"], "sourceAvailable": disponible,
                "available": False, "bodyPath": None, "exercises": [],
            }
            if a.modo == "completo" and disponible:
                datos = (a.curso / m["fichero"]).read_bytes()
                local = f"{cid}/lessons/{lid}.md"
                (a.salida / local).parent.mkdir(parents=True, exist_ok=True)
                (a.salida / local).write_bytes(datos)
                h = sha256(datos)
                copias.append({"sourcePath": m["fichero"], "localPath": local,
                               "sourceSha256": h, "copySha256": h, "transformation": None})
                leccion.update(available=True, bodyPath=local, exercises=[
                    {"id": id_academia(cid, e["id"]), "lessonId": lid, "title": e["titulo"]}
                    for e in m.get("ejercicios", [])])
            lecciones.append(leccion)
            enlaces.append({"sourcePath": m["fichero"], "lessonId": lid, "fragments": {}})

    manifiesto = {
        "schemaVersion": 2,
        "source": {"name": curso["titulo"], "versionRef": a.commit},
        "course": {"id": cid, "namespace": cid, "title": curso["titulo"]},
        "phases": fases,
        "lessons": lecciones,
        "resources": [],
        "sourceToId": enlaces,
        "projectStages": [],
        "provenance": {"files": copias, "generated": []},
    }
    raiz.mkdir(parents=True, exist_ok=True)
    (raiz / "manifest.json").write_text(json.dumps(manifiesto, ensure_ascii=False, indent=2) + "\n",
                                        encoding="utf-8")
    disponibles = sum(1 for l in lecciones if l["available"])
    ejercicios = sum(len(l["exercises"]) for l in lecciones)
    print(f"{cid}: {len(fases)} fases, {len(lecciones)} lecciones ({disponibles} con cuerpo), "
          f"{ejercicios} ejercicios -> {raiz / 'manifest.json'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
