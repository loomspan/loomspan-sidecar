"""Discover and execute a skill using an existing execution credential."""

from argparse import ArgumentParser
from getpass import getpass
from sys import stdin

from loomspan_sdk import CredentialSnapshot, SidecarClient


def run(base_url: str, skill_name: str, text: str, credential: CredentialSnapshot) -> None:
    with SidecarClient(base_url) as client:
        catalog = client.list_skills(credential)
        skill = client.get_skill(skill_name, credential)
        accepted = client.submit(skill.name, {"text": text}, credential)
        current = client.get_execution(accepted.id, credential)
        terminal = client.wait_for_completion(accepted.id, credential)
        print(f"Discovered {len(catalog)} skill(s).")
        print(f"Execution {accepted.id}: {current.status} -> {terminal.status}")


if __name__ == "__main__":
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("base_url")
    parser.add_argument("skill_name")
    parser.add_argument("text", help="Text to send as the skill's input")
    args = parser.parse_args()
    token = getpass("Execution credential: ") if stdin.isatty() else stdin.readline().rstrip("\r\n")
    run(args.base_url, args.skill_name, args.text, CredentialSnapshot(token))
