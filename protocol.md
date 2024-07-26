# nowindcompanion

# Nowind Interface Host <> MSX Commumication Protocol Description

## Objective

This protocol allow an MSX Home Computer to communicate with an USB host.
The communication includes but is not limited to accessing disk-images on the host device.
The host device can be a PC or any device with USB OTG-support like a mobile phone.

## Scope

This protocol applies specifically to the Nowind Interface V1, developed by Jan Wilmans and Aaldert Dekker between 2010 and 2012. It is also applicable to the Nowind Interface V2, produced and sold by Sunrise. Although the V2 version features more internal SRAM and a smaller form factor, the protocols for both versions are identical.
The 'Nowind Interface' is a dedicated cartridge designed to be a low cost way to let an MSX computer access modern hardware.

The protocol was specifically designed around the hardware properties of the 'Nowind Interface' and contains specific sequences to deal with data loss.
If the hardware used is not identical to the original design it is unlikely to function properly without modifications to the protocol.

## Definitions and Abbreviations

- USB Host Device: a PC running Windows, Linux or MacOS, or an android phone. (IPhone not supported)
- Nowind Interface: the physical cartridge that is inserted into the MSX Home Computer
- Nowind Protocol: the communication protocol between the MSX Disk ROM and and the USB Host Device
- Nowind Companion: Nowind Host Application Software for Android phones
- MSX: MSX Host Computer
- nwhostapp: Nowind Host Application Software for Linux, Windows and MacOS.

## Roles and Responsibilities

The USB Host Device is a service waiting for commands from the Nowind Interface connected to the MSX. When the MSX attempts to read from a disk this request is send to the Host where it reads from a file instead of a physical disk.
This relationship can in theory be reversed so the the MSX starts waiting for commands from the Host. There is some preliminary work done to make this possible but there are no actual commands implemented.

Most commands (such a read/write to disk) follow a pattern roughly like this:

- the Host is waiting for a command
- the MSX sends a command
- the Host queues a series of replies
- the MSX starts waiting for a reply, reads it and sends back a 'marker' to acknowledge the reply.
- the Host checks the 'marker' and queues a re-transmission if it is incorrect.
- the MSX keeps waiting for more replies until it receives a 'done' command, or a timeout occurs.
- the Host returns to a 'waiting for command' state after all replies have been acknowledged or a timeout occurs.

## Materials and Equipment

For the system to operate you will need:

- a physical Nowind Interface cartridge
- an MSX Host Computer
- a USB Host Device

Alternatively, the whole operation including the MSX Home Computer, the Nowind Interface and the Host application can be emulated using the [Nowind MSX Emulator](https://github.com/janwilmans/nowindlibraries/tree/master/emuv1)

## Procedure



Describe the step-by-step procedure in detail.
Use numbered steps for clarity.
Ensure each step is clear and unambiguous.
Provide diagrams or images if necessary.

8. Safety precautions and Compliance requirements.

If you connect your host PC to an MSX Host Computer it is recommended to connect only ONE of them to the earth ground.
MSX Computers typically do not have a earth ground lead, but it is possible to create a ground loop through a monitor or applifier!
Make sure nothing attachted to the MSX powered by a grounded lead.

Note that is also possible to create this problem using a phone if you charge the phone using a grounded device.
Although problems with these restrictions are extremly rare in practice, failing to comply to these requirements can permanently damage the MSX Computer and the Nowind Interface.

9. Quality Control
Outline quality control measures to ensure the protocol is followed correctly.
Include checkpoints and criteria for quality assurance.
10. Troubleshooting
Provide a troubleshooting guide for common issues that may arise during the procedure.
11. Documentation and Reporting
Specify the documentation requirements for recording results and observations.
Include templates or forms if applicable.
12. References
List all references used to develop the protocol.
Include scientific articles, books, or standard operating procedures (SOPs).
13. Appendix
Include any additional information that supports the protocol, such as detailed calculations, additional figures, or extended data.