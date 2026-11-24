"""Discover and execute a skill asynchronously with an existing credential."""

import asyncio
from argparse import ArgumentParser
from getpass import getpass
from sys import stdin

from loomspan_sdk import AsyncSidecarClient, CredentialSnapshot


async def run(base_url: str, namespace: str, local_name: str, text: str, credential: CredentialSnapshot) -> None:
    async with AsyncSidecarClient(base_url) as client:
        catalog = await client.list_skills(namespace, credential)
        target = next((skill for skill in catalog
                       if (skill.namespace, skill.local_name) == (namespace, local_name)), None)
        if target is None:
            raise ValueError(f"Skill {namespace}:{local_name} was not found")
        skill = await client.get_skill(target.namespace, target.local_name, credential)
        accepted = await client.submit(skill.namespace, skill.local_name, {"text": text}, credential)
        current = await client.get_execution(accepted.id, credential)
        terminal = await client.wait_for_completion(accepted.id, credential)
        print(f"Discovered {len(catalog)} skill(s).")
        print(f"Execution {accepted.id}: {current.status} -> {terminal.status}")


if __name__ == "__main__":
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("base_url")
    parser.add_argument("namespace")
    parser.add_argument("local_name")
    parser.add_argument("text", help="Text to send as the skill's input")
    args = parser.parse_args()
    token = getpass("Execution credential: ") if stdin.isatty() else stdin.readline().rstrip("\r\n")
    asyncio.run(run(args.base_url, args.namespace, args.local_name, args.text, CredentialSnapshot(token)))
